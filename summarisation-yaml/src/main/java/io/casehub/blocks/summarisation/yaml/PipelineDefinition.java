package io.casehub.blocks.summarisation.yaml;

import java.util.List;

public record PipelineDefinition(String name, SourceDefinition source, List<LevelDefinition> levels) {}
