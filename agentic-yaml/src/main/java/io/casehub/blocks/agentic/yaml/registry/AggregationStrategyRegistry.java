package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.yaml.spec.AggregationSpec;

public class AggregationStrategyRegistry {

    @SuppressWarnings("unchecked")
    public <T> AggregationStrategy<T> resolve(AggregationSpec spec) {
        return switch (spec) {
            case AggregationSpec.PassThrough pt -> new PassThrough<>();
            case AggregationSpec.CollectAll ca -> new CollectAll<>();
            case AggregationSpec.MajorityVote mv -> new MajorityVote<>();
            case AggregationSpec.Auction au ->
                    throw new UnsupportedOperationException(
                            "auction requires BidExtractor — use CDI injection");
        };
    }
}
