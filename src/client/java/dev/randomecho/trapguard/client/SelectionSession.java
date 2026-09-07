package dev.randomecho.trapguard.client;

import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.Region;
import dev.randomecho.trapguard.core.model.TrapDefinition;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Ephemeral editor state. This intentionally contains no Minecraft classes so selection semantics
 * can be reused by later version ports.
 */
public final class SelectionSession {
    private IntPos pos1;
    private IntPos pos2;
    private final LinkedHashSet<IntPos> extraBlocks = new LinkedHashSet<>();
    private String editingTrapName;
    private ComparisonMode draftMode = ComparisonMode.STRUCTURAL;

    public IntPos pos1() {
        return pos1;
    }

    public IntPos pos2() {
        return pos2;
    }

    public Set<IntPos> extraBlocks() {
        return Set.copyOf(extraBlocks);
    }

    public Optional<String> editingTrapName() {
        return Optional.ofNullable(editingTrapName);
    }

    public ComparisonMode draftMode() {
        return draftMode;
    }

    public ComparisonMode toggleDraftMode() {
        draftMode = draftMode == ComparisonMode.STRUCTURAL ? ComparisonMode.EXACT : ComparisonMode.STRUCTURAL;
        return draftMode;
    }

    public void setPos1(IntPos pos) {
        this.pos1 = pos;
    }

    public void setPos2(IntPos pos) {
        this.pos2 = pos;
    }

    public Optional<Region> mainRegion() {
        if (pos1 == null || pos2 == null) return Optional.empty();
        return Optional.of(Region.between(pos1, pos2));
    }

    public boolean toggleExtra(IntPos pos) {
        if (extraBlocks.remove(pos)) return false;
        extraBlocks.add(pos);
        return true;
    }

    public boolean containsExtra(IntPos pos) {
        return extraBlocks.contains(pos);
    }

    public boolean addExtra(IntPos pos) {
        return extraBlocks.add(pos);
    }

    public boolean removeExtra(IntPos pos) {
        return extraBlocks.remove(pos);
    }

    /** Loads the first persisted region into the simple v0.1 editor and preserves all extras. */
    public void loadFrom(TrapDefinition trap) {
        clear();
        if (trap.regions != null && !trap.regions.isEmpty()) {
            Region first = trap.regions.getFirst();
            pos1 = first.min();
            pos2 = first.max();
        }
        if (trap.extraBlocks != null) extraBlocks.addAll(trap.extraBlocks);
        editingTrapName = trap.name;
        draftMode = trap.mode == null ? ComparisonMode.STRUCTURAL : trap.mode;
    }

    public void clear() {
        pos1 = null;
        pos2 = null;
        extraBlocks.clear();
        editingTrapName = null;
        draftMode = ComparisonMode.STRUCTURAL;
    }
}
