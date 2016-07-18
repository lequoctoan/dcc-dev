package org.icgc.dcc.dev.portal;

import java.io.File;
import java.util.List;

import org.icgc.dcc.dev.log.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Synchronized;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortalService {

  @Value("${workspace.dir}")
  File workspaceDir;

  @Autowired
  PortalRepository repository;
  @Autowired
  LogService logs;
  @Autowired
  PortalFileSystem fileSystem;
  @Autowired
  PortalDeployer deployer;
  @Autowired
  PortalExecutor executor;
  @Autowired
  PortalCandidateResolver candidates;

  public List<Portal.Candidate> getCandidates() {
    return candidates.resolve();
  }

  public List<Portal> list() {
    return repository.list();
  }

  public Portal get(String id) {
    return repository.get(id);
  }

  @Synchronized
  public Portal create(String prNumber, String name, String title, String description, String ticket) {
    log.info("Creating portal {}...", name);
    val candidate = candidates.resolve(prNumber);
    if (candidate == null) {
      return null;
    }

    // Collect metadata
    val portal = new Portal()
        .setName(name)
        .setTitle(title)
        .setDescription(description)
        .setTicket(ticket)
        .setTarget(candidate);

    // Create directory with aritfact
    deployer.deploy(portal);

    // Save the metadata
    repository.save(portal);
    val output = executor.start(portal.getId());
    log.info("Output: {}", output);
    
    logs.tail(fileSystem.getLogFile(portal.getId()));

    return portal;
  }

  @Synchronized
  public Portal update(String id, String name, String title, String description, String ticket) {
    log.info("Updating portal {}...", id);
    val portal = repository.get(id);

    repository.save(portal
        .setName(name)
        .setTitle(title)
        .setDescription(description)
        .setTicket(ticket));

    deployer.update(portal);

    return portal;
  }

  @Synchronized
  public void remove(String id) {
    log.info("Removing portal {}...", id);
    stop(id);
    deployer.undeploy(id);
  }

  public void start(String id) {
    log.info("Starting portal {}...", id);
    executor.start(id);
  }

  public void stop(String id) {
    log.info("Stopping portal {}...", id);
    executor.stop(id);
  }

  public void restart(String id) {
    log.info("Restarting portal {}...", id);
    executor.restart(id);
  }
  
  public String status(String id) {
    log.info("Getting status of portal {}...", id);
    return executor.status(id);
  }

}
