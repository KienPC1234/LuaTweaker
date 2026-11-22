package com.luatweaker.dimension;

import com.luatweaker.content.DatapackServiceImpl;

/**
 * In-memory datapack double for biome tests.
 *
 * <p>Reuses the production implementation so the double can never drift from the
 * real virtual-pack semantics; tests seed and read back through
 * {@code addData}/{@code getData}.
 */
final class BiomesFakeDatapack extends DatapackServiceImpl {
}
