// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#pragma once

#include <mutex>

#include "exec/pipeline/context_with_dependency.h"
#include "exec/pipeline/scan/balanced_chunk_buffer.h"
#include "exec/vectorized/olap_scan_prepare.h"
#include "runtime/global_dict/parser.h"
#include "util/phmap/phmap_fwd_decl.h"

namespace starrocks {

class ScanNode;

namespace vectorized {
class RuntimeFilterProbeCollector;
}

namespace pipeline {

class OlapScanContext;
using OlapScanContextPtr = std::shared_ptr<OlapScanContext>;
class OlapScanContextFactory;
using OlapScanContextFactoryPtr = std::shared_ptr<OlapScanContextFactory>;

using namespace vectorized;

class OlapScanContext final : public ContextWithDependency {
public:
    explicit OlapScanContext(vectorized::OlapScanNode* scan_node, int32_t dop, bool shared_scan,
                             BalancedChunkBuffer& chunk_buffer)
            : _scan_node(scan_node), _chunk_buffer(chunk_buffer), _shared_scan(shared_scan), _scan_dop(dop) {}

    Status prepare(RuntimeState* state);
    void close(RuntimeState* state) override;

    void set_prepare_finished() { _is_prepare_finished.store(true, std::memory_order_release); }
    bool is_prepare_finished() const { return _is_prepare_finished.load(std::memory_order_acquire); }

    Status parse_conjuncts(RuntimeState* state, const std::vector<ExprContext*>& runtime_in_filters,
                           RuntimeFilterProbeCollector* runtime_bloom_filters);

    vectorized::OlapScanNode* scan_node() const { return _scan_node; }
    vectorized::OlapScanConjunctsManager& conjuncts_manager() { return _conjuncts_manager; }
    const std::vector<ExprContext*>& not_push_down_conjuncts() const { return _not_push_down_conjuncts; }
    const std::vector<std::unique_ptr<OlapScanRange>>& key_ranges() const { return _key_ranges; }
    BalancedChunkBuffer& get_chunk_buffer() { return _chunk_buffer; }

    // Shared scan states
    bool is_shared_scan() const { return _shared_scan; }
    // Attach and detach to account the active input for shared chunk buffer
    void attach_shared_input(int32_t operator_seq, int32_t source_index);
    void detach_shared_input(int32_t operator_seq, int32_t source_index);
    bool has_active_input() const;
    BalancedChunkBuffer& get_shared_buffer();

private:
    vectorized::OlapScanNode* _scan_node;

    std::vector<ExprContext*> _conjunct_ctxs;
    vectorized::OlapScanConjunctsManager _conjuncts_manager;
    // The conjuncts couldn't push down to storage engine
    std::vector<ExprContext*> _not_push_down_conjuncts;
    std::vector<std::unique_ptr<OlapScanRange>> _key_ranges;
    vectorized::DictOptimizeParser _dict_optimize_parser;
    ObjectPool _obj_pool;

    // For shared_scan mechanism
    using ActiveInputKey = std::pair<int32_t, int32_t>;
    using ActiveInputSet = phmap::parallel_flat_hash_set<
            ActiveInputKey, typename phmap::Hash<ActiveInputKey>, typename phmap::EqualTo<ActiveInputKey>,
            typename std::allocator<ActiveInputKey>, NUM_LOCK_SHARD_LOG, std::mutex, true>;
    BalancedChunkBuffer& _chunk_buffer; // Shared Chunk buffer for all scan operators, owned by OlapScanContextFactory.
    ActiveInputSet _active_inputs;      // Maintain the active chunksource
    bool _shared_scan;                  // Enable shared_scan
    int32_t _scan_dop;                  // DOP of scan operator

    std::atomic<bool> _is_prepare_finished{false};
};

// OlapScanContextFactory creates different contexts for each scan operator, if _shared_scan is false.
// Otherwise, it outputs the same context for each scan operator.
class OlapScanContextFactory {
public:
    OlapScanContextFactory(vectorized::OlapScanNode* const scan_node, int32_t dop, bool shared_morsel_queue,
                           bool shared_scan, ChunkBufferLimiterPtr chunk_buffer_limiter)
            : _scan_node(scan_node),
              _dop(dop),
              _shared_morsel_queue(shared_morsel_queue),
              _shared_scan(shared_scan),
              _chunk_buffer(shared_scan ? BalanceStrategy::kRoundRobin : BalanceStrategy::kDirect, dop,
                            std::move(chunk_buffer_limiter)),
              _contexts(shared_morsel_queue ? 1 : dop) {}

    OlapScanContextPtr get_or_create(int32_t driver_sequence);

private:
    vectorized::OlapScanNode* const _scan_node;
    const int32_t _dop;
    const bool _shared_morsel_queue;   // Whether the scan operators share a morsel queue.
    const bool _shared_scan;           // Whether the scan operators share a chunk buffer.
    BalancedChunkBuffer _chunk_buffer; // Shared Chunk buffer for all the scan operators.

    std::vector<OlapScanContextPtr> _contexts;
};

} // namespace pipeline

} // namespace starrocks
