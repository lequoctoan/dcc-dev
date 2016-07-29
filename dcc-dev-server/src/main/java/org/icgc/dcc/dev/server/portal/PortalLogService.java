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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.icgc.dcc.dev.server.message.MessageService;
import org.icgc.dcc.dev.server.message.Messages.LogMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.google.common.io.Files;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortalLogService {

  /**
   * Dependencies.
   */
  @Autowired
  PortalFileSystem fileSystem;
  @Autowired
  MessageService messages;

  /**
   * State.
   */
  Map<String, Tailer> tailers = Maps.newConcurrentMap();
  ExecutorService executor = Executors.newCachedThreadPool();

  @SneakyThrows
  public String cat(String portalId) {
    val logFile = fileSystem.getLogFile(portalId);
    return Files.toString(logFile, StandardCharsets.UTF_8);
  }

  @Synchronized
  public void startTailing(@NonNull String portalId) {
    if (!tailers.containsKey(portalId)) {
      val logFile = fileSystem.getLogFile(portalId);
      log.info("Tailing portal {}: {}...", portalId, logFile);

      val tailer = new Tailer(logFile, this.new LogListener(portalId));
      tailers.put(portalId, tailer);

      executor.execute(tailer);
    }
  }

  @Synchronized
  public void stopTailing(@NonNull String portalId) {
    val tailer = tailers.remove(portalId);
    if (tailer == null) return;

    tailer.stop();
  }

  @PreDestroy
  public void shutdown() {
    tailers.values().forEach(Tailer::stop);
  }

  @RequiredArgsConstructor
  private class LogListener extends TailerListenerAdapter {

    final String portalId;

    @Override
    public void handle(String line) {
      log.info("{}: {}", portalId, line);
      val message = new LogMessage().setLine(line).setPortalId(portalId);
      messages.sendMessage(message);
    }

  }

}