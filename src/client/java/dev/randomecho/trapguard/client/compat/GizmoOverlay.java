package dev.randomecho.trapguard.client.compat;

import dev.randomecho.trapguard.client.SelectionSession;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.Region;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import dev.randomecho.trapguard.core.monitor.Difference;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/**
 * 1.21.11-specific visualization adapter. If rendering APIs change in a future Minecraft release,
 * this class can be replaced without touching TrapGuard's persisted model or comparison engine.
 */
public final class GizmoOverlay {
    private static final int SELECTION_STROKE = 0xE600E5FF;
    private static final int EXTRA_STROKE = 0xE65CFF79;
    private static final int SAVED_STROKE = 0xB3FFD54A;
    private static final int BROKEN_STROKE = 0xFFFF3040;
    private static final int BROKEN_FILL = 0x33FF3040;
    private static final int FOCUS_STROKE = 0xFFFF4DFF;
    private static final int FOCUS_FILL = 0x33FF4DFF;
    private static final int LIFE_MILLIS = 80;

    private static final GizmoStyle SELECTION_STYLE = GizmoStyle.stroke(SELECTION_STROKE, 2.0F);
    private static final GizmoStyle EXTRA_STYLE = GizmoStyle.stroke(EXTRA_STROKE, 2.0F);
    private static final GizmoStyle SAVED_STYLE = GizmoStyle.stroke(SAVED_STROKE, 1.5F);
    private static final GizmoStyle BROKEN_STYLE = GizmoStyle.strokeAndFill(BROKEN_STROKE, 2.5F, BROKEN_FILL);
    private static final GizmoStyle FOCUS_STYLE = GizmoStyle.strokeAndFill(FOCUS_STROKE, 3.0F, FOCUS_FILL);

    private GizmoOverlay() {}

    public static void renderSelection(Minecraft client, SelectionSession selection) {
        if (client.level == null) return;
        selection.mainRegion().ifPresent(region -> cuboid(region, SELECTION_STYLE));
        for (IntPos pos : selection.extraBlocks()) {
            Gizmos.cuboid(MinecraftBlockAdapter.toMinecraft(pos), EXTRA_STYLE).persistForMillis(LIFE_MILLIS);
        }
    }

    public static void renderTrap(TrapDefinition trap, Map<IntPos, Difference> activeDifferences) {
        if (trap.overlay) {
            for (Region region : trap.regions) cuboid(region, SAVED_STYLE);
            for (IntPos pos : trap.extraBlocks) {
                Gizmos.cuboid(MinecraftBlockAdapter.toMinecraft(pos), EXTRA_STYLE).persistForMillis(LIFE_MILLIS);
            }
        }
        for (IntPos pos : activeDifferences.keySet()) {
            Gizmos.cuboid(MinecraftBlockAdapter.toMinecraft(pos), 0.0125F, BROKEN_STYLE)
                    .persistForMillis(LIFE_MILLIS);
        }
    }

    public static void renderFocus(IntPos pos) {
        Gizmos.cuboid(MinecraftBlockAdapter.toMinecraft(pos), 0.025F, FOCUS_STYLE).persistForMillis(LIFE_MILLIS);
    }

    private static void cuboid(Region region, GizmoStyle style) {
        // AABB max values are exclusive spatial edges, while Region max coordinates are inclusive blocks.
        AABB box = new AABB(
                region.min().x(), region.min().y(), region.min().z(),
                region.max().x() + 1.0, region.max().y() + 1.0, region.max().z() + 1.0
        );
        Gizmos.cuboid(box, style).persistForMillis(LIFE_MILLIS);
    }
}
