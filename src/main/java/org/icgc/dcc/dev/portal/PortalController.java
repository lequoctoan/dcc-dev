package org.icgc.dcc.dev.portal;

import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.util.List;
import java.util.Map;

import org.icgc.dcc.dev.portal.Portal.Candidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
public class PortalController {

  @Autowired
  PortalService service;

  @RequestMapping(value = "/candidates", method = GET)
  public List<Candidate> getCandidates() {
    return service.getCandidates();
  }

  @RequestMapping(value = "/portals", method = GET)
  public List<Portal> list() {
    return service.list();
  }

  @RequestMapping(value = "/portals", method = POST)
  public Portal create(
      @RequestParam(value = "pr", required = true) String pr,

      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "ticket", required = false) String ticket,
      @RequestParam(value = "properties", required = false) Map<String, String> properties) {
    return service.create(pr, name, title, description, ticket, properties);
  }

  @RequestMapping(value = "/portals/{id}", method = PUT)
  public Portal update(
      @PathVariable("id") String id,

      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "ticket", required = false) String ticket,
      @RequestParam(value = "properties", required = false) Map<String, String> properties) {
    return service.update(id, name, title, description, ticket, properties);
  }

  @RequestMapping(value = "/portals/{id}", method = GET)
  public Portal get(@PathVariable("id") String id) {
    return service.get(id);
  }

  @RequestMapping(value = "/portals/{id}/status", method = GET)
  public String status(@PathVariable("id") String id) {
    return service.status(id);
  }

  @RequestMapping(value = "/portals/{id}", method = DELETE)
  public void remove(@PathVariable("id") String id) {
    service.remove(id);
  }

  @RequestMapping(value = "/portals/{id}/start", method = POST)
  public void start(@PathVariable("id") String id) {
    service.start(id);
  }

  @RequestMapping(value = "/portals/{id}/stop", method = POST)
  public void stop(@PathVariable("id") String id) {
    service.stop(id);
  }

  @RequestMapping(value = "/portals/{id}/restart", method = POST)
  public void restart(@PathVariable("id") String id) {
    service.restart(id);
  }

}
