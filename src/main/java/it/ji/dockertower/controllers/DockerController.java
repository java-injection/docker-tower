package it.ji.dockertower.controllers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/docker")
@Slf4j
public class DockerController {
    private final DockerClient dockerClient;

    public DockerController() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .build();

        dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    @PostMapping("/update/{containerId}")
    public ResponseEntity<String> updateContainer(
            @PathVariable String containerId,
            @RequestBody UpdateRequest request) {
        try {
            // 1. Get existing container config
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId)
                    .exec();

            // 2. Stop the container
            dockerClient.stopContainerCmd(containerId)
                    .withTimeout(30)
                    .exec();
            log.info("Container {} stopped", containerId);

            // 3. Remove the container
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();
            log.info("Container {} removed", containerId);

            // 4. Pull the new image
            dockerClient.pullImageCmd(request.getImageName())
                    .withTag(request.getImageTag())
                    .start()
                    .awaitCompletion(30, TimeUnit.SECONDS);
            log.info("Image {}:{} pulled", request.getImageName(), request.getImageTag());

            // 5. Create and start new container with the same settings
            CreateContainerCmd createCommand = dockerClient.createContainerCmd(
                            request.getImageName() + ":" + request.getImageTag())
                    .withName(request.getContainerName());

            // Copy existing container configuration if no new config provided
            if (request.getHostConfig() == null) {
                createCommand.withHostConfig(containerInfo.getHostConfig());
            } else {
                createCommand.withHostConfig(request.getHostConfig());
            }

            CreateContainerResponse container = createCommand.exec();

            dockerClient.startContainerCmd(container.getId())
                    .exec();
            log.info("New container {} started", container.getId());

            return ResponseEntity.ok("Container updated successfully");
        } catch (Exception e) {
            log.error("Error updating container", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating container: " + e.getMessage());
        }
    }
}

