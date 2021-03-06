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
package org.icgc.dcc.dev.server.portal.io;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.NonNull;

/**
 * Abstraction that encapsulates the file layout of a portal instance.
 */
@Component
public class PortalFileSystem {

  /**
   * Configuration.
   */
  @Value("${workspace.dir}")
  File workspaceDir;
  @Value("${artifact.artifactId}")
  String baseName;

  public File getDir() {
    return new File(workspaceDir, "portals");
  }

  public File getRootDir(@NonNull Integer portalId) {
    return new File(getDir(), String.valueOf(portalId));
  }

  public File getBinDir(@NonNull Integer portalId) {
    return new File(getRootDir(portalId), "bin");
  }

  public File getSettingsFile(@NonNull Integer portalId) {
    return new File(getConfDir(portalId), "application.yml");
  }

  public File getConfDir(@NonNull Integer portalId) {
    return new File(getRootDir(portalId), "conf");
  }

  public File getLibDir(@NonNull Integer portalId) {
    return new File(getRootDir(portalId), "lib");
  }

  public File getLogsDir(@NonNull Integer portalId) {
    return new File(getRootDir(portalId), "logs");
  }

  public File getScriptFile(@NonNull Integer portalId) {
    return new File(getBinDir(portalId), baseName);
  }

  public File getJarFile(@NonNull Integer portalId) {
    return new File(getLibDir(portalId), baseName + ".jar");
  }

  public File getLogFile(@NonNull Integer portalId) {
    return new File(getLogsDir(portalId), baseName + ".log");
  }

}
