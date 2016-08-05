/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.dev.server.portal;

import static org.icgc.dcc.common.core.util.stream.Collectors.toImmutableList;
import static org.icgc.dcc.dev.server.message.Messages.PortalChangeMessage.portalChange;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.icgc.dcc.dev.server.jira.JiraService;
import org.icgc.dcc.dev.server.jira.JiraTicket;
import org.icgc.dcc.dev.server.message.MessageService;
import org.icgc.dcc.dev.server.message.Messages.PortalChangeMessage;
import org.icgc.dcc.dev.server.message.Messages.PortalChangeMessage.Type;
import org.icgc.dcc.dev.server.portal.candidate.PortalCandidateResolver;
import org.icgc.dcc.dev.server.portal.io.PortalDeployer;
import org.icgc.dcc.dev.server.portal.io.PortalExecutor;
import org.icgc.dcc.dev.server.portal.io.PortalFileSystem;
import org.icgc.dcc.dev.server.portal.io.PortalLogs;
import org.icgc.dcc.dev.server.portal.util.PortalLocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.slugify.Slugify;

import lombok.Cleanup;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * Main service responsible for coordinating the life cycle management of portal instances.
 * <p>
 * One of the main aspects this service provides is locking semantics.
 */
@Slf4j
@Service
public class PortalService {

  /**
   * Configuration.
   */
  @Value("${server.publicUrl}")
  URL publicUrl;

  /**
   * Dependencies.
   */
  @Autowired
  PortalCandidateResolver candidates;
  @Autowired
  PortalRepository repository;
  @Autowired
  PortalFileSystem fileSystem;
  @Autowired
  PortalLogs logs;
  @Autowired
  PortalDeployer deployer;
  @Autowired
  PortalExecutor executor;
  @Autowired
  PortalLocks locks;
  @Autowired
  MessageService messages;
  @Autowired
  JiraService jira;

  public List<Portal.Candidate> getCandidates() {
    return candidates.resolve();
  }

  public Portal get(@NonNull Integer portalId) {
    return find(portalId).orElseThrow(() -> new PortalNotFoundException(portalId));
  }

  public Portal getBySlug(@NonNull String slug) {
    return findBySlug(slug).orElseThrow(() -> new PortalNotFoundException(slug));
  }

  public Portal.Status getStatus(@NonNull Integer portalId) {
    @Cleanup
    val lock = locks.lockReading(portalId);
    if (!repository.exists(portalId)) throw new PortalNotFoundException(portalId);

    return executor.getStatus(portalId);
  }

  public List<Portal> list() {
    return repository.getIds().stream()
        .map(this::find)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableList());
  }

  public Portal create(@NonNull Integer prNumber, String slug, String title, String description, String ticket,
      Map<String, String> config, boolean start) {
    log.info("Creating portal for PR {}...", prNumber);

    // Validate
    validateSlug(slug);
    validateSlugUniqueness(slug, null);

    // Resolve portal candidate by PR
    val candidate = candidates.resolve(prNumber).orElseThrow(() -> new PortalPrNotFoundException(prNumber));

    // Get new id and lock
    val portalId = deployer.nextPortalId();
    @Cleanup
    val lock = locks.lockWriting(portalId);

    // Collect metadata in a single object
    val portal = new Portal()
        .setId(portalId)
        .setTitle(resolveTitle(title, null, candidate.getPr().getTitle()))
        .setSlug(resolveSlug(slug, null, title, null, candidate.getPr().getTitle()))
        .setDescription(resolveDescription(description, null, candidate.getPr().getDescription()))
        .setTicketKey(resolveTicketKey(ticket, null, candidate.getTicket()))
        .setConfig(resolveConfig(config, null))
        .setTarget(candidate);

    // Create directory
    deployer.init(portal);

    // Install jar
    deployer.deploy(portal);

    // Save instance
    repository.create(portal);

    // Assign URL
    portal.setUrl(resolveUrl(publicUrl, portal));
    repository.update(portal);

    if (start) {
      // Start the portal
      start(portal.getId());

      // Ensure ticket is marked for test with the portal URL
      updateTicket(portal);
    }

    notifyChange(portal, Type.CREATED);

    return portal;
  }

  public void update(Portal portal) {
    @Cleanup
    val lock = locks.lockWriting(portal);
    repository.update(portal);

    val status = getStatus(portal.getId());
    if (status.isRunning()) {
      executor.stop(portal);
    }

    deployer.deploy(portal);
    executor.startAsync(portal);

    notifyChange(portal, Type.UPDATED);
  }

  public Portal update(@NonNull Integer portalId, String slug, String title, String description, String ticket,
      Map<String, String> config) {
    log.info("Updating portal {}...", portalId);

    // Validate
    validateSlug(slug);
    validateSlugUniqueness(slug, portalId);

    @Cleanup
    val lock = locks.lockWriting(portalId);
    val portal = get(portalId);

    val candidate = portal.getTarget();
    repository.update(portal
        .setTitle(resolveTitle(title, portal.getTitle(), candidate.getPr().getTitle()))
        .setSlug(resolveSlug(slug, portal.getSlug(), title, portal.getTitle(), candidate.getPr().getTitle()))
        .setDescription(resolveDescription(description, portal.getDescription(), candidate.getPr().getDescription()))
        .setTicketKey(resolveTicketKey(ticket, portal.getTicketKey(), candidate.getTicket()))
        .setConfig(resolveConfig(config, portal.getConfig())));

    executor.stop(portal);
    deployer.deploy(portal);
    executor.startAsync(portal);

    notifyChange(portal, Type.UPDATED);

    return portal;
  }

  public void remove() {
    log.info("**** Removing all portals!");
    for (val portal : list()) {
      remove(portal.getId());
    }
  }

  public void remove(@NonNull Integer portalId) {
    log.info("Removing portal {}...", portalId);

    @Cleanup
    val lock = locks.lockWriting(portalId);
    val portal = get(portalId);

    // Wait for the instance to stop (synchronous)
    executor.stop(portal);

    deployer.undeploy(portalId);

    notifyChange(portal, Type.REMOVED);
  }

  public void start(@NonNull Integer portalId) {
    log.info("Starting portal {}...", portalId);

    @Cleanup
    val lock = locks.lockWriting(portalId);
    val portal = get(portalId);

    executor.startAsync(portal);
  }

  public void restart(@NonNull Integer portalId) {
    log.info("Restarting portal {}...", portalId);

    @Cleanup
    val lock = locks.lockWriting(portalId);
    val portal = get(portalId);

    executor.restartAsync(portal);
  }

  public void stop(@NonNull Integer portalId) {
    log.info("Stopping portal {}...", portalId);

    @Cleanup
    val lock = locks.lockWriting(portalId);
    val portal = get(portalId);

    executor.stopAsync(portal);
  }

  public String getLog(Integer portalId) {
    log.info("Getting log of portal {}...", portalId);

    @Cleanup
    val lock = locks.lockReading(portalId);
    return logs.cat(portalId);
  }

  private Optional<Portal> find(@NonNull Integer portalId) {
    @Cleanup
    val lock = locks.lockReading(portalId);
    return repository.find(portalId);
  }

  private Optional<Portal> findBySlug(@NonNull String slug) {
    return list().stream().filter(p -> p.getSlug().equals(slug)).findFirst();
  }

  private void notifyChange(Portal portal, PortalChangeMessage.Type type) {
    messages.sendMessage(portalChange().type(type).portal(portal).build());
  }

  private void updateTicket(Portal portal) {
    val ticketKey = portal.getTicketKey();
    if (ticketKey == null) return;

    val iframeUrl = publicUrl + "/" + portal.getId();
    jira.updateTicket(ticketKey, "Deployed to " + iframeUrl + " for testing");
  }

  @SneakyThrows
  private void validateSlug(String slug) {
    if (slug == null) return;

    if (slug.trim().equals("")) {
      throw new PortalValidationException("Portal slug '%s' cannot be blank", slug);
    }

    val slugifiedSlug = new Slugify().slugify(slug);
    if (!slug.equals(slugifiedSlug)) {
      throw new PortalValidationException("Portal slug '%s' is not slugified. Should be '%s'", slug, slugifiedSlug);
    }
  }

  private void validateSlugUniqueness(String slug, Integer portalId) {
    val existingPortal = findBySlug(slug);
    if (existingPortal.isPresent() && !existingPortal.get().getId().equals(portalId)) {
      throw new PortalValidationException("Portal %s already exists with slug '%s'", existingPortal.get().getId(),
          slug);
    }
  }

  @SneakyThrows
  private static String resolveSlug(String newSlug, String currentSlug, String newTitle, String currentTitle,
      String prTitle) {
    return new Slugify().slugify(resolveValue(newSlug, currentSlug, prTitle));
  }

  private static String resolveTitle(String newTitle, String currentTitle, String prTitle) {
    return resolveValue(newTitle, currentTitle, prTitle);
  }

  private static String resolveDescription(String newDescription, String currentDescription, String prDescription) {
    return resolveValue(newDescription, currentDescription, prDescription);
  }

  private static String resolveTicketKey(String newTicketKey, String currentTicketKey, JiraTicket currentTicket) {
    return resolveValue(newTicketKey, currentTicketKey, currentTicket != null ? currentTicket.getKey() : null);
  }

  private static Map<String, String> resolveConfig(Map<String, String> newConfig, Map<String, String> currentConfig) {
    return resolveValue(newConfig, currentConfig);
  }

  private static String resolveUrl(URL publicUrl, Portal portal) {
    // Strip this port and add portal port
    val url = publicUrl.toString();
    val port = portal.getSystemConfig().get("server.port");

    return UriComponentsBuilder.fromHttpUrl(url).port(port).toUriString();
  }

  @SafeVarargs
  private static <T> T resolveValue(T... values) {
    for (val value : values) {
      if (value != null) return value;
    }

    return null;
  }

}
