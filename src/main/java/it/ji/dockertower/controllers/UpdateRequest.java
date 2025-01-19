package it.ji.dockertower.controllers;

import com.github.dockerjava.api.model.HostConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
class UpdateRequest {
    private String imageName;
    private String imageTag;
    private String containerName;
    private HostConfig hostConfig;
}
