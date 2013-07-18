package de.blablubbabc.paintball.gadgets.handlers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import de.blablubbabc.paintball.Match;
import de.blablubbabc.paintball.Origin;
import de.blablubbabc.paintball.Paintball;
import de.blablubbabc.paintball.gadgets.Gadget;
import de.blablubbabc.paintball.gadgets.WeaponHandler;
import de.blablubbabc.paintball.gadgets.events.PaintballHitEvent;
import de.blablubbabc.paintball.statistics.player.PlayerStat;
import de.blablubbabc.paintball.statistics.player.PlayerStats;
import de.blablubbabc.paintball.utils.Translator;
import de.blablubbabc.paintball.utils.Utils;

public class MarkerHandler extends WeaponHandler {
	
	public MarkerHandler(int customItemTypeID, boolean useDefaultType) {
		super(customItemTypeID, useDefaultType, new Origin() {
			
			@Override
			public String getKillMessage(String killerName, String victimName, ChatColor killerColor, ChatColor victimColor, String feedColorCode) {
				Map<String, String> vars = new HashMap<String, String>();
				vars.put("killer", killerName);
				vars.put("killer_color", killerColor.toString());
				vars.put("target", victimName);
				vars.put("target_color", victimColor.toString());
				vars.put("feed_color", Paintball.instance.feeder.getFeedColor());
				
				return Translator.getString("WEAPON_FEED_MARKER", vars);
			}
		});
	}

	@Override
	protected int getDefaultItemTypeID() {
		return Material.SNOW_BALL.getId();
	}

	@Override
	protected ItemStack setItemMeta(ItemStack itemStack) {
		ItemMeta meta = itemStack.getItemMeta();
		meta.setDisplayName(Translator.getString("WEAPON_PAINTBALL"));
		itemStack.setItemMeta(meta);
		return itemStack;
	}
	
	@Override
	protected void onInteract(PlayerInteractEvent event, Match match) {
		if (event.getAction() == Action.PHYSICAL) return;
		Player player = event.getPlayer();
		String playerName = player.getName();
		ItemStack itemInHand = player.getItemInHand();
		if (itemInHand == null) return;
		
		if (itemInHand.isSimilar(getItem())) {
			PlayerInventory inv = player.getInventory();
			if (match.setting_balls == -1 || inv.contains(Material.SNOW_BALL, 1)) {
				World world = player.getWorld();
				Vector direction = player.getLocation().getDirection().normalize();
				Location spawnLoc = Utils.getRightHeadLocation(direction, player.getEyeLocation());
				
				// SOUND EFFECT
				player.playSound(spawnLoc, Sound.CLICK, 1.0F, 2F);
				world.playSound(spawnLoc, Sound.CHICKEN_EGG_POP, 2.0F, 2F);
				
				// SHOOT SNOWBALL
				Snowball snowball = (Snowball) world.spawnEntity(spawnLoc, EntityType.SNOWBALL);
				snowball.setShooter(player);
				// REGISTER:
				Paintball.instance.weaponManager.getBallHandler().createBall(match, player, snowball, this.getWeaponOrigin());
				// BOOST:
				snowball.setVelocity(direction.multiply(Paintball.instance.speedmulti));
				// STATS
				// PLAYERSTATS
				PlayerStats playerStats = Paintball.instance.playerManager.getPlayerStats(playerName);
				playerStats.addStat(PlayerStat.SHOTS, 1);
				// INFORM MATCH
				match.onShot(player);
				
				if (match.setting_balls != -1) {
					// -1 ball
					Utils.removeInventoryItems(inv, getItem(), 1);
				}
				
			} else {
				player.playSound(player.getEyeLocation(), Sound.FIRE_IGNITE, 1F, 2F);
			}
			Utils.updatePlayerInventoryLater(Paintball.instance, player);
		}
	}
	
	@Override
	protected void onProjectileHit(ProjectileHitEvent event, Projectile projectile, Match match, Player shooter) {
		if (projectile.getType() == EntityType.SNOWBALL) {
			String shooterName = shooter.getName();
			Gadget ball = Paintball.instance.weaponManager.getBallHandler().getBall(projectile, match, shooterName);
			// is paintball ?
			if (ball != null) {
				Location location = projectile.getLocation();
				
				// effect
				if (Paintball.instance.effects) {
					if (match.isBlue(shooter)) {
						location.getWorld().playEffect(location, Effect.POTION_BREAK, 0);
					} else if (match.isRed(shooter)) {
						location.getWorld().playEffect(location, Effect.POTION_BREAK, 5);
					}
				}
				
				// call event for others:
				Paintball.instance.getServer().getPluginManager().callEvent(new PaintballHitEvent(event, match, shooter));
				
				// remove ball from tracking:
				ball.dispose(true);
			}
		}
	}
	
	@Override
	public void cleanUp(Match match, String playerName) {
		// nothing to do here
	}

	@Override
	public void cleanUp(Match match) {
		// nothing to do here
	}
	
}
