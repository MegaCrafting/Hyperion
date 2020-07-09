/*
 *   Copyright 2016, 2017, 2020 Moros <https://github.com/PrimordialMoros>
 *
 * 	  This file is part of Hyperion.
 *
 *    Hyperion is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Hyperion is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with Hyperion.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.primordialmoros.hyperion.abilities.waterbending.combo;

import com.github.primordialmoros.hyperion.Hyperion;
import com.github.primordialmoros.hyperion.abilities.waterbending.IceCrawl;
import com.github.primordialmoros.hyperion.methods.CoreMethods;
import com.github.primordialmoros.hyperion.util.MaterialCheck;
import com.github.primordialmoros.hyperion.util.RegenTempBlock;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.util.BlockSource;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.ice.PhaseChange;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class IceDrill extends IceAbility implements AddonAbility, ComboAbility {
	private final Set<Block> blocks = new HashSet<>();
	private final List<BlockIterator> lines = new ArrayList<>();

	private long cooldown;
	private int sourceRange;
	private int maxLength;
	private long duration;
	private long regenDelay;

	private boolean started;
	private Location origin;
	private Location tip;

	public IceDrill(Player player) {
		super(player);

		if (hasAbility(player, IceDrill.class) || !bPlayer.canBendIgnoreBinds(this) || !bPlayer.canIcebend()) {
			return;
		}

		cooldown = Hyperion.getPlugin().getConfig().getLong("Abilities.Water.WaterCombo.IceDrill.Cooldown");
		sourceRange = Hyperion.getPlugin().getConfig().getInt("Abilities.Water.WaterCombo.IceDrill.SourceRange");
		maxLength = Hyperion.getPlugin().getConfig().getInt("Abilities.Water.WaterCombo.IceDrill.MaxLength");
		duration = Hyperion.getPlugin().getConfig().getInt("Abilities.Water.WaterCombo.IceDrill.Duration");
		regenDelay = Hyperion.getPlugin().getConfig().getLong("Abilities.Water.WaterCombo.IceDrill.RegenBlockTime");

		sourceRange = (int) getNightFactor(sourceRange, player.getWorld());

		started = false;

		if (grabSource()) {
			if (hasAbility(player, IceCrawl.class)) {
				getAbility(player, IceCrawl.class).remove();
			}
			start();
		}
	}

	@Override
	public void progress() {
		if (!bPlayer.canBendIgnoreBindsCooldowns(this) || !player.isSneaking()) {
			remove();
			return;
		}

		if (hasAbility(player, IceCrawl.class)) {
			getAbility(player, IceCrawl.class).remove();
		}

		if (started) {
			ListIterator<BlockIterator> iterator = lines.listIterator();
			while (iterator.hasNext()) {
				if (ThreadLocalRandom.current().nextInt(1 + lines.size()) == 0) continue;
				BlockIterator blockLine = iterator.next();
				if (blockLine.hasNext()) {
					formIce(blockLine.next());
				} else {
					iterator.remove();
				}
			}

			if (lines.isEmpty()) {
				formIce(tip.getBlock());
				remove();
			}
		} else {
			playFocusWaterEffect(origin.getBlock());
		}
	}

	private void formIce(Block block) {
		if (blocks.contains(block) || TempBlock.isTempBlock(block) || MaterialCheck.isUnbreakable(block) || GeneralMethods.isRegionProtectedFromBuild(this, block.getLocation())) {
			return;
		}
		String matName = block.getType().name();
		RegenTempBlock regenBlock = new RegenTempBlock(block, Material.PACKED_ICE.createBlockData(), duration);
		if (!matName.equals(Material.WATER.name())) {
			regenBlock.setRevertTask(() -> new RegenTempBlock(block, Material.AIR.createBlockData(), regenDelay));
		}
		PhaseChange.getFrozenBlocksMap().put(regenBlock, player);
		blocks.add(block);
	}


	private void prepareCone() {
		maxLength = (int) getNightFactor(maxLength, origin.getWorld());
		final Vector direction = GeneralMethods.getDirection(origin, GeneralMethods.getTargetedLocation(player, sourceRange + maxLength)).normalize();
		tip = origin.clone().add(direction.clone().multiply(maxLength));
		final Location targetLocation = origin.clone().add(direction.clone().multiply(maxLength - 1));
		int radius = (int) Math.ceil(0.2 * maxLength);
		for (Location testLoc : GeneralMethods.getCircle(origin, radius, 1, false, true, 0)) {
			if (!GeneralMethods.isRegionProtectedFromBuild(this, testLoc) && (isWater(testLoc.getBlock()) || isIce(testLoc.getBlock()))) {
				lines.add(CoreMethods.blockRayTrace(testLoc.getBlock(), targetLocation.getBlock()));
			}
		}

		if (lines.size() < 5) {
			remove();
			return;
		}
		started = true;
	}

	private boolean grabSource() {
		final Block sourceBlock = BlockSource.getWaterSourceBlock(player, sourceRange, ClickType.SHIFT_DOWN, true, true, false);
		if (!GeneralMethods.isAdjacentToThreeOrMoreSources(sourceBlock) || GeneralMethods.isRegionProtectedFromBuild(this, sourceBlock.getLocation())) {
			return false;
		}
		playFocusWaterEffect(sourceBlock);
		origin = sourceBlock.getLocation().clone();
		return true;
	}

	@Override
	public boolean isEnabled() {
		return Hyperion.getPlugin().getConfig().getBoolean("Abilities.Water.WaterCombo.IceDrill.Enabled");
	}

	@Override
	public String getName() {
		return "IceDrill";
	}

	@Override
	public String getDescription() {
		return Hyperion.getPlugin().getConfig().getString("Abilities.Water.WaterCombo.IceDrill.Description");
	}

	@Override
	public String getAuthor() {
		return Hyperion.getAuthor();
	}

	@Override
	public String getVersion() {
		return Hyperion.getVersion();
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return origin;
	}

	@Override
	public List<Location> getLocations() {
		return blocks.stream().map(Block::getLocation).collect(Collectors.toList());
	}

	@Override
	public Object createNewComboInstance(Player player) {
		return new IceDrill(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		ArrayList<AbilityInformation> combination = new ArrayList<>();
		combination.add(new AbilityInformation("FrostBreath", ClickType.LEFT_CLICK));
		combination.add(new AbilityInformation("FrostBreath", ClickType.LEFT_CLICK));
		combination.add(new AbilityInformation("IceCrawl", ClickType.SHIFT_DOWN));
		return combination;
	}

	@Override
	public String getInstructions() {
		return "FrostBreath (Left Click) > FrostBreath (Left Click) > IceCrawl (Hold Sneak) > IceCrawl (Left Click to form spike in the direction you are looking)";
	}

	@Override
	public void load() {
	}

	@Override
	public void stop() {
	}

	@Override
	public void remove() {
		if (!blocks.isEmpty()) {
			bPlayer.addCooldown(this);
		}
		super.remove();
	}

	public static void setClicked(Player p) {
		if (hasAbility(p, IceDrill.class)) {
			getAbility(p, IceDrill.class).setClicked();
		}
	}

	private void setClicked() {
		if (started) return;
		prepareCone();
	}
}
