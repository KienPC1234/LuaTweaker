package com.luatweaker.client;

import com.luatweaker.content.DatapackServiceImpl;

/**
 * In-memory {@link com.luatweaker.api.content.IDatapackService} double for shader tests.
 *
 * <p>Reuses the production implementation so the double can never drift from the
 * real virtual-pack semantics; tests read back through {@code getVirtualFiles()}.
 */
final class FakeDatapack extends DatapackServiceImpl {
}
