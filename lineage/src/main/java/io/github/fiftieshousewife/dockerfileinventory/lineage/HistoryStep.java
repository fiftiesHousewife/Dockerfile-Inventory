package io.github.fiftieshousewife.dockerfileinventory.lineage;

/**
 * One entry of an image config's history: the instruction that ran ({@code created_by}), the builder's comment,
 * and whether it made a layer. Steps that made layers line up, in order, with {@code rootfs.diff_ids}.
 */
public record HistoryStep(String createdBy, String comment, boolean emptyLayer, String created) {
}
