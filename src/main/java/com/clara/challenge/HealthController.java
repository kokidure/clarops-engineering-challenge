package com.clara.challenge;

import com.clara.challenge.health.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class HealthController {

  private final HealthService healthService;

  @GetMapping("/health")
  public String index() {
    return healthService.getMessage();
  }
}
