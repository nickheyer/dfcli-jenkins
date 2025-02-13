package io.jenkins.plugins.dfcli.models;

import lombok.Getter;
import lombok.Setter;

/**
 * The output JSON model of the 'df rt build-publish' command.
 *
 * @author yahavi
 **/
@Getter
@Setter
public class BuildInfoOutputModel {
    private String buildInfoUiUrl;
}
