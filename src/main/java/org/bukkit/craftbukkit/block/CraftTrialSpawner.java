package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.TrialSpawner;

public class CraftTrialSpawner extends CraftBlockEntityState<TrialSpawnerBlockEntity> implements TrialSpawner {

    public CraftTrialSpawner(World world, TrialSpawnerBlockEntity tileEntity) {
        super(world, tileEntity);
    }

    protected CraftTrialSpawner(CraftTrialSpawner state, Location location) {
        super(state, location);
    }

    @Override
    public CraftTrialSpawner copy() {
        return new CraftTrialSpawner(this, null);
    }

    @Override
    public CraftTrialSpawner copy(Location location) {
        return new CraftTrialSpawner(this, location);
    }
}
