package pe.axiz.reflectionpoc.infrastructure.web;

import org.springframework.web.bind.annotation.*;
import pe.axiz.reflectionpoc.infrastructure.reflection.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reflection")
public class ReflectionController {
    private final ReflectionPluginRegistry registry;

    public ReflectionController(ReflectionPluginRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/plugins")
    public List<PluginDescriptor> plugins() {
        return registry.list();
    }

    @GetMapping("/plugins/{code}")
    public PluginDescriptor plugin(@PathVariable String code) {
        return registry.descriptor(code);
    }

    @GetMapping("/plugins/{code}/inspect")
    public ClassInspection inspect(@PathVariable String code) {
        return registry.inspect(code);
    }

    @PostMapping("/plugins/reload")
    public List<PluginDescriptor> reload() {
        return registry.reload();
    }

    @GetMapping("/metrics")
    public ReflectionMetrics metrics() {
        return registry.metrics();
    }
}
