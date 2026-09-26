package gg.shrekmc.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface SpawnCrowd {
    boolean isActive(World world);

    @Nullable Location getCenter(World world);

    void setCenter(Location center);

    void resetCenter(World world);

    double getRadius(World world);

    void setRadius(World world, double radius);

    void resetRadius(World world);

    int getMaxVisiblePlayers();

    void setMaxVisiblePlayers(int maxVisiblePlayers);

    void resetMaxVisiblePlayers();

    boolean isInside(Location location);

    boolean isInside(Player player);
}
