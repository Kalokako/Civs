package org.redcastlemedia.multitallented.civs.regions;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.redcastlemedia.multitallented.civs.Civs;
import org.redcastlemedia.multitallented.civs.LocaleManager;
import org.redcastlemedia.multitallented.civs.util.CVItem;

import java.util.*;

public class RegionManager {
    private HashMap<String, ArrayList<Region>> regions = new HashMap<>();
    private HashMap<String, RegionType> regionTypes = new HashMap<>();
    private static RegionManager regionManager;
    private HashMap<String, Integer> itemCheck = new HashMap<>();

    public RegionManager() {
        regionManager = this;
    }

    public void addRegion(Region region) {
        String worldName = region.getLocation().getWorld().getName();
        if (!regions.containsKey(worldName)) {
            regions.put(worldName, new ArrayList<Region>());
        }
        regions.get(worldName).add(region);
        Collections.sort(regions.get(worldName),
                new Comparator<Region>() {
                    @Override
                    public int compare(Region r1, Region r2) {
                        if (r1.getLocation().getX() - r1.getRadiusXN() > r2.getLocation().getX() - r2.getRadiusXN()) {
                            return 1;
                        } else if (r1.getLocation().getX() - r1.getRadiusXN() < r2.getLocation().getX() - r2.getRadiusXN()) {
                            return -1;
                        }
                        return 0;
                    }
                });
    }

    public Region getRegionAt(Location location) {
        String worldName = location.getWorld().getName();
        if (regions.get(worldName) == null || regions.get(worldName).isEmpty()) {
            return null;
        }

        int index;
        double mindex = 0;
        double maxdex = regions.get(worldName).size() -1;
        double prevIndex = 0;
        for (;;) {
            index = (int) Math.round(((maxdex - mindex) / 2) + mindex);
            Region r = regions.get(worldName).get(index);
            if (prevIndex == index) {
                if (withinRegion(r, location)) {
                    return r;
                } else {
                    return null;
                }
            }

            if (withinRegion(r, location)) {
                return r;
            } else if (location.getX() < r.getLocation().getX() - r.getRadiusXN()) {

                maxdex = index;
            } else if (location.getX() > r.getLocation().getX() + r.getRadiusXN()) {

                mindex = index;
            } else {
                return findRegion((int) mindex, (int) maxdex, location, index);
            }
            prevIndex = index;
        }
    }

    private Region findRegion(int index1, int index2, Location location, int index) {
        String worldName = location.getWorld().getName();
        for (int i=index1; i<index2; i++) {
            if (i==index) {
                continue;
            }
            if (withinRegion(regions.get(worldName).get(i), location)) {
                return regions.get(worldName).get(i);
            }
        }
        return null;
    }

    private boolean withinRegion(Region region, Location location) {
        Location rLocation = region.getLocation();
        return rLocation.getX() - region.getRadiusXN() <= location.getX() &&
                rLocation.getX() + 1 +region.getRadiusXP() >= location.getX() &&
                rLocation.getY() - region.getRadiusYN() <= location.getY() &&
                rLocation.getY() + 1 + region.getRadiusYP() >= location.getY() &&
                rLocation.getZ() - region.getRadiusZN() <= location.getZ() &&
                rLocation.getZ() + 1 + region.getRadiusZP() >= location.getZ();
    }

    public void loadRegionType(FileConfiguration config) {
        String name = config.getString("name");
        HashSet<CVItem> reqs = new HashSet<>();
        for (String req : config.getStringList("requirements")) {
            reqs.add(CVItem.createCVItemFromString(req));
        }
        HashSet<String> effects = new HashSet<>();
        for (String effect : config.getStringList("effects")) {
            effects.add(effect);
        }
        int buildRadius = config.getInt("build-radius", 5);
        int buildRadiusX = config.getInt("build-radius-x", buildRadius);
        int buildRadiusY = config.getInt("build-radius-y", buildRadius);
        int buildRadiusZ = config.getInt("build-radius-z", buildRadius);
        int effectRadius = config.getInt("effect-radius", buildRadius);
        regionTypes.put(name.toLowerCase(), new RegionType(
                name,
                reqs,
                effects,
                buildRadius,
                buildRadiusX,
                buildRadiusY,
                buildRadiusZ,
                effectRadius));
    }

    public RegionType getRegionType(String name) {
        return regionTypes.get(name);
    }

    void detectNewRegion(BlockPlaceEvent event) {
        LocaleManager localeManager = LocaleManager.getInstance();
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        String regionTypeName = block.getState().getData().toItemStack().getItemMeta().getDisplayName();
        regionTypeName = regionTypeName.replace("Civs ", "");

        RegionType regionType = getRegionType(regionTypeName.toLowerCase());

        if (regionType == null) {
            event.setCancelled(true);
            player.sendMessage(Civs.getPrefix() +
                    localeManager.getTranslation("en", "no-region-type-found")
                            .replace("$1", regionTypeName)); //TODO get the player's language
            return;
        }

        itemCheck.clear();
        for (CVItem currentItem : regionType.getReqs()) {
            itemCheck.put(currentItem.getMat() + ":" + currentItem.getDamage(), currentItem.getQty());
        }
        int[] radii = new int[6];
        radii[0] = 0;
        radii[1] = 0;
        radii[2] = 0;
        radii[3] = 0;
        radii[4] = 0;
        radii[5] = 0;

        World currentWorld = block.getLocation().getWorld();
        Location location = block.getLocation();
        int biggestXZRadius = Math.max(regionType.getBuildRadiusX(), regionType.getBuildRadiusZ());
        int xMax = (int) location.getX() + 1 + (int) ((double) biggestXZRadius * 1.5);
        int xMin = (int) location.getX() - (int) ((double) biggestXZRadius * 1.5);
        int yMax = (int) location.getY() + 1 + (int) ((double) regionType.getBuildRadiusY() * 1.5);
        int yMin = (int) location.getY() - (int) ((double) regionType.getBuildRadiusY() * 1.5);
        int zMax = (int) location.getZ() + 1 + (int) ((double) biggestXZRadius * 1.5);
        int zMin = (int) location.getZ() - (int) ((double) biggestXZRadius * 1.5);

        yMax = yMax > currentWorld.getMaxHeight() ? currentWorld.getMaxHeight() : yMax;
        yMin = yMin < 0 ? 0 : yMin;

        boolean hasReqs = false;
        outer: for (int x=xMin; x<xMax;x++) {
            for (int y=yMin; y<yMax; y++) {
                for (int z=zMin; z<zMax; z++) {
                    Block currentBlock = currentWorld.getBlockAt(x,y,z);
                    if (currentBlock == null) {
                        continue;
                    }


                    String wildCardString = currentBlock.getType() + ":-1";
                    String damageString = currentBlock.getType() + ":";
                    if (currentBlock.getState() != null) {
                        damageString += currentBlock.getState().getData().toItemStack().getDurability();
                    }

                    if (itemCheck.containsKey(wildCardString)) {
                        itemCheck.put(wildCardString, itemCheck.get(wildCardString) - 1);
                        hasReqs = checkIfScanFinished();
                        adjustRadii(radii, location, x, y, z);

                    } else if (itemCheck.containsKey(damageString)) {
                        itemCheck.put(damageString, itemCheck.get(damageString) - 1);
                        hasReqs = checkIfScanFinished();
                        adjustRadii(radii, location, x, y, z);
                    }
                    if (hasReqs) {
                        break outer;
                    }
                }
            }
        }

        if (!radiusCheck(radii, regionType)) {
            //TODO send Error message
            event.setCancelled(true);
            player.sendMessage(Civs.getPrefix() + "You're building is to big to be a " + regionTypeName);
            return;
        }

        if (hasReqs) {
            HashSet<UUID> owners = new HashSet<>();
            owners.add(player.getUniqueId());
            HashSet<UUID> members = new HashSet<>();
            addRegion(new Region(regionType.getName(), owners, members, block.getLocation(), radii));
        } else {
            event.setCancelled(true);
            player.sendMessage(Civs.getPrefix() + "You haven't placed the required blocks to make a " + regionTypeName);
        }
    }

    private void adjustRadii(int[] radii, Location location, int x, int y, int z) {
        int currentRelativeX = x - (int) location.getX();
        int currentRelativeY = y - (int) location.getY();
        int currentRelativeZ = z - (int) location.getZ();
        if (currentRelativeX < 0) {
            currentRelativeX = Math.abs(currentRelativeX);
            radii[2] = radii[2] > currentRelativeX ? radii[2] : currentRelativeX;
        } else if (currentRelativeX > 0) {
            radii[0] = radii[0] > currentRelativeX ? radii[0] : currentRelativeX;
        }
        if (currentRelativeY < 0) {
            currentRelativeY = Math.abs(currentRelativeY);
            radii[5] = radii[5] > currentRelativeY ? radii[5] : currentRelativeY;
        } else if (currentRelativeY > 0) {
            radii[4] = radii[4] > currentRelativeY ? radii[4] : currentRelativeY;
        }
        if (currentRelativeZ < 0) {
            currentRelativeZ = Math.abs(currentRelativeZ);
            radii[3] = radii[3] > currentRelativeZ ? radii[3] : currentRelativeZ;
        } else if (currentRelativeZ > 0) {
            radii[1] = radii[1] > currentRelativeZ ? radii[1] : currentRelativeZ;
        }
    }

    private boolean radiusCheck(int[] radii, RegionType regionType) {
        int xRadius = regionType.getBuildRadiusX();
        int yRadius = regionType.getBuildRadiusY();
        int zRadius = regionType.getBuildRadiusZ();
        boolean xRadiusBigger = xRadius > zRadius;
        boolean xRadiusActuallyBigger = radii[0] + radii[2] > radii[1] + radii[3];
        if ((xRadiusActuallyBigger && xRadiusBigger && radii[0] + radii[2] > xRadius * 2) ||
                xRadiusActuallyBigger && !xRadiusBigger && radii[0] + radii[2] > zRadius * 2) {
            return false;
        } else {
            while ((radii[0] + radii[2] < xRadius * 2 && xRadiusActuallyBigger) ||
                    (radii[0] + radii[2] < zRadius * 2 && !xRadiusActuallyBigger)) {
                if (radii[0] < radii[2]) {
                    radii[0]++;
                } else {
                    radii[2]++;
                }
            }
        }
        if (radii[4] + radii[5] > yRadius * 2) {
            return false;
        } else {

            while (radii[4] + radii[5] < yRadius * 2) {
                if (radii[4] < radii[5]) {
                    radii[4]++;
                } else {
                    radii[5]++;
                }
            }
        }
        if ((!xRadiusActuallyBigger && !xRadiusBigger && radii[1] + radii[3] > zRadius * 2) ||
                !xRadiusActuallyBigger && xRadiusBigger && radii[1] + radii[3] > xRadius * 2) {
            return false;
        } else {
            while ((radii[1] + radii[3] < zRadius * 2 && xRadiusActuallyBigger) ||
                    (radii[1] + radii[3] < xRadius * 2 && !xRadiusActuallyBigger)) {
                if (radii[1] < radii[3]) {
                    radii[1]++;
                } else {
                    radii[3]++;
                }
            }
        }
        return true;
    }

    private boolean checkIfScanFinished() {
        for (String key : itemCheck.keySet()) {
            if (itemCheck.get(key) > 0) {
                return false;
            }
        }
        return true;
    }

    public Set<Region> getRegionEffectsAt(Location location, int modifier) {
        String worldName = location.getWorld().getName();
        HashSet<Region> effects = new HashSet<>();
        for (int i=regions.get(worldName).size() - 1; i>-1; i--) {
            Region region = regions.get(worldName).get(i);
            boolean withinX = location.getX() > region.getLocation().getX() - region.getRadiusXN() - modifier &&
                    location.getX() < region.getLocation().getX() + region.getRadiusXP() + 1 + modifier;
            boolean withinY = location.getY() > region.getLocation().getY() - region.getRadiusYN() - modifier &&
                    location.getY() < region.getLocation().getY() + region.getRadiusYP() + 1 + modifier;
            boolean withinZ = location.getZ() > region.getLocation().getZ() - region.getRadiusZN() - modifier &&
                    location.getZ() < region.getLocation().getZ() + region.getRadiusZP() + 1 + modifier;

            if (withinX && withinY && withinZ) {
                effects.add(region);
                continue;
            }
            if (location.getX() > region.getLocation().getX() - region.getRadiusXN() - modifier) {
                break;
            }
        }
        return effects;
    }

    public static synchronized RegionManager getInstance() {
        if (regionManager == null) {
            regionManager = new RegionManager();
        }
        return regionManager;
    }
}
