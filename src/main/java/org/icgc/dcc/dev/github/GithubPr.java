package org.icgc.dcc.dev.github;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GithubPr {

  String number;
  String title;
  String description;
  String user;
  String branch;
  String url;
  String head;
  String avatarUrl;

}
