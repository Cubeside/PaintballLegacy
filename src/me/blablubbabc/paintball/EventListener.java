package me.blablubbabc.paintball;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import me.blablubbabc.paintball.extras.Airstrike;
import me.blablubbabc.paintball.extras.Grenade;
import me.blablubbabc.paintball.extras.Mine;
import me.blablubbabc.paintball.extras.Rocket;
import me.blablubbabc.paintball.extras.Turret;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

public class EventListener implements Listener {
	private Paintball plugin;
	private MatchManager mm;

	private HashMap<Player, Integer> taskIds;
	private HashSet<Byte> transparent;
	private long lastSignUpdate = 0;

	// private HashMap<Player, String> chatMessages;

	public EventListener(Paintball pl) {
		plugin = pl;
		mm = plugin.mm;
		taskIds = new HashMap<Player, Integer>();
		// chatMessages = new HashMap<Player, String>();

		transparent = new HashSet<Byte>();
		transparent.add((byte) 0);
		transparent.add((byte) 8);
		transparent.add((byte) 10);
		transparent.add((byte) 51);
		transparent.add((byte) 90);
		transparent.add((byte) 119);
		transparent.add((byte) 321);
		transparent.add((byte) 85);

	}

	// /////////////////////////////////////////
	// EVENTS

	@EventHandler(ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (mm.getMatch(player) != null) {
			Match match = mm.getMatch(player);
			if (!match.started) {
				if (event.getFrom().getX() != event.getTo().getX()
						|| event.getFrom().getZ() != event.getTo().getZ()) {
					event.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onSignCreate(SignChangeEvent event) {
		if (event.isCancelled())
			return;
		Player player = event.getPlayer();
		String l = ChatColor.stripColor(event.getLine(0));

		for (String s : plugin.sql.sqlPlayers.statsList) {
			if (s.equals("teamattacks"))
				s = "ta";
			if (s.equals("hitquote"))
				s = "hq";
			if (s.equals("airstrikes"))
				s = "as";
			if (s.equals("money_spent"))
				s = "spent";

			if (l.equalsIgnoreCase("[PB " + s.toUpperCase() + "]")) {
				if (!player.isOp() && !player.hasPermission("paintball.admin")) {
					event.setCancelled(true);
					player.sendMessage(plugin.t.getString("NO_PERMISSION"));
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityDeath(EntityDeathEvent event) {
		if (event.getEntityType() == EntityType.SNOWMAN) {
			Snowman snowman = (Snowman) event.getEntity();
			Turret turret = Turret.isTurret(snowman);
			if (turret != null) {
				turret.die(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onInteract(PlayerInteractEvent event) {
		if (event.getClickedBlock() != null) {
			Block block = event.getClickedBlock();
			BlockState state = block.getState();
			if (state instanceof Sign) {
				Sign sign = (Sign) state;
				String l = ChatColor.stripColor(sign.getLine(0));

				for (String stat : plugin.sql.sqlPlayers.statsList) {
					String s = stat;
					if (s.equals("teamattacks"))
						s = "ta";
					if (s.equals("hitquote"))
						s = "hq";
					if (s.equals("airstrikes"))
						s = "as";
					if (s.equals("money_spent"))
						s = "spent";

					if (l.equalsIgnoreCase("[PB " + s.toUpperCase() + "]")) {
						changeSign(event.getPlayer().getName(), sign, stat);
						break;
					}
				}
			}
		}
	}

	private void changeSign(String player, Sign sign, String stat) {
		if ((System.currentTimeMillis() - lastSignUpdate) > (500)) {
			HashMap<String, String> vars = new HashMap<String, String>();
			vars.put("player", player);
			if (plugin.pm.exists(player)) {
				if (stat.equals("hitquote") || stat.equals("kd")) {
					DecimalFormat dec = new DecimalFormat("###.##");
					float statF = (float) (Integer) plugin.pm.getStats(player)
							.get(stat) / 100;
					vars.put("value", dec.format(statF));
				} else
					vars.put("value", "" + plugin.pm.getStats(player).get(stat));
			} else
				vars.put("value", plugin.t.getString("NOT_FOUND"));
			sign.setLine(1, plugin.t.getString("SIGN_LINE_TWO", vars));
			sign.setLine(2, plugin.t.getString("SIGN_LINE_THREE", vars));
			sign.setLine(3, plugin.t.getString("SIGN_LINE_FOUR", vars));
			sign.update();
			lastSignUpdate = System.currentTimeMillis();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void onPlayerHit(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Projectile) {
			Projectile shot = (Projectile) event.getDamager();
			if (shot.getShooter() instanceof Player) {
				Player shooter = (Player) shot.getShooter();
				Match match = mm.getMatch(shooter);
				if(match != null) {
					if (event.getEntity() instanceof Player) {
						Player target = (Player) event.getEntity();						
						if (shooter != target) {
							if (mm.getMatch(shooter) != null
									&& mm.getMatch(target) != null) {
								if (mm.getMatch(shooter) == mm.getMatch(target)) {
									if (!match.isSpec(shooter)
											&& !match.isSpec(target)
											&& match.isSurvivor(shooter)
											&& match.isSurvivor(target)
											&& match.started) {
										// Geschoss?
										if (shot instanceof Snowball) {
											// match
											match.hitSnow(target, shooter);
										}
									}
								}
							}
						}
					} else if (event.getEntityType() == EntityType.SNOWMAN) {
						Snowman snowman = (Snowman) event.getEntity();
						Turret turret = Turret.isTurret(snowman);
						if (turret != null && match == turret.match && match.enemys(shooter, turret.player)) {
							turret.hit();
						}
					}
				}
				}
		} else if (event.getDamager() instanceof Player
				&& event.getEntity() instanceof Player
				&& event.getCause() == DamageCause.ENTITY_ATTACK) {
			Player attacker = (Player) event.getDamager();
			Player target = (Player) event.getEntity();
			if (attacker != target) {
				if (mm.getMatch(attacker) != null
						&& mm.getMatch(target) != null) {
					if (mm.getMatch(attacker) == mm.getMatch(target)) {
						Match match = mm.getMatch(attacker);
						if (match.enemys(attacker, target)
								&& match.isSurvivor(attacker)
								&& match.isSurvivor(target) && match.started) {
							if (plugin.allowMelee) {
								if (target.getHealth() > plugin.meleeDamage)
									target.setHealth(target.getHealth()
											- plugin.meleeDamage);
								else {
									plugin.mm.getMatch(target).death(target);
								}
							}
						}
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onFireballExplosion(EntityExplodeEvent event) {
		Entity entity = event.getEntity();
		if (entity != null && entity.getType() == EntityType.FIREBALL) {
			if (Rocket.isRocket((Fireball) entity) != null) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerShoot(ProjectileLaunchEvent event) {
		if (event.getEntity().getShooter() instanceof Player) {
			Player player = (Player) event.getEntity().getShooter();
			// TEST lights
			// LightEntity.addEntity(event.getEntity(), plugin);
			if (Lobby.getTeam(player) != null) {
				if (mm.getMatch(player) != null && Lobby.isPlaying(player)
						&& mm.getMatch(player).isSurvivor(player)) {
					if (mm.getMatch(player).started) {
						Projectile shot = (Projectile) event.getEntity();
						Vector v = shot.getVelocity();
						// Geschoss?
						if (shot instanceof Snowball) {
							// z�hlen
							mm.getMatch(player).shot(player);
							if (plugin.balls == -1) {
								// +1 ball
								player.getInventory().addItem(
										new ItemStack(Material.SNOW_BALL, 1));
							}
							// boosting:
							shot.setVelocity(v.multiply(plugin.speedmulti));
						} else if (shot instanceof Egg) {
							if (plugin.grenades) {
								Grenade.eggThrow(player, (Egg) shot);
								// z�hlen
								mm.getMatch(player).grenade(player);
								if (plugin.grenadeAmount == -1) {
									// +1grenade
									player.getInventory().addItem(
											new ItemStack(Material.EGG, 1));
								}
								// boosting:
								shot.setVelocity(v
										.multiply(plugin.grenadeSpeed));
							}
						}
					} else {
						event.setCancelled(true);
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onEggThrow(PlayerEggThrowEvent event) {
		if (event.getEgg().getShooter() instanceof Player) {
			Player player = (Player) event.getEgg().getShooter();
			if (Lobby.getTeam(player) != null) {
				event.setHatching(false);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInventory(InventoryClickEvent event) {
		if (event.getWhoClicked() instanceof Player) {
			Player player = (Player) event.getWhoClicked();
			if (Lobby.getTeam(player) != null) {
				if (event.getSlotType() != SlotType.CONTAINER
						&& event.getSlotType() != SlotType.QUICKBAR
						&& event.getSlotType() != SlotType.OUTSIDE) {
					event.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteractPlayer(PlayerInteractEntityEvent event) {
		Player player = (Player) event.getPlayer();
		if (plugin.giftsEnabled && Lobby.getTeam(player) != null) {
			if(player.getItemInHand().getType() == Material.CHEST) {
				if(event.getRightClicked() instanceof Player) {
					Player receiver = (Player) event.getRightClicked();
					if(Lobby.getTeam(receiver) != null) {
						plugin.christmas.giveGift(player, receiver);
					}
				}
			}
		}
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player player = (Player) event.getPlayer();
		if (Lobby.getTeam(player) != null) {
			if(player.getItemInHand().getType() == Material.CHEST && plugin.giftsEnabled) {
				//to prevent placing:
				event.setCancelled(true);
				if(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					plugin.christmas.unwrapGift(player);
				}
			}
			Match match = mm.getMatch(player);
			if (match != null && Lobby.isPlaying(player) && match.started
					&& match.isSurvivor(player)) {
				// AIRSTRIKE
				if (player.getItemInHand().getTypeId() == 280) {
					if (event.getAction() == Action.LEFT_CLICK_AIR
							|| event.getAction() == Action.RIGHT_CLICK_AIR) {
						if (Airstrike.marked(player)) {
							if (Airstrike.getAirstrikes(match).size() < plugin.airstrikeMatchLimit) {
								if (Airstrike.getAirstrikes(player).size() < plugin.airstrikePlayerLimit) {
									Airstrike.call(plugin, player, match);
									// z�hlen
									match.airstrike(player);
									// remove stick if not infinite
									if (match.setting_airstrikes != -1) {
										ItemStack i = player.getItemInHand();
										if (i.getAmount() <= 1)
											player.setItemInHand(null);
										else {
											i.setAmount(i.getAmount() - 1);
											player.setItemInHand(i);
										}
									}
								} else {
									player.sendMessage(plugin.t
											.getString("AIRSTRIKE_PLAYER_LIMIT_REACHED"));
								}

							} else {
								player.sendMessage(plugin.t
										.getString("AIRSTRIK_MATCH_LIMIT_REACHED"));
							}
						}
					}
					// GRENADE
				} else if (player.getItemInHand().getTypeId() == 344) {
					if (event.getAction() == Action.RIGHT_CLICK_AIR
							|| event.getAction() == Action.RIGHT_CLICK_BLOCK) {
						player.sendMessage(plugin.t.getString("GRENADE_THROW"));
						player.playSound(player.getLocation(),
								Sound.SILVERFISH_IDLE, 100L, 1L);
					}
					// ROCKET
				} else if (player.getItemInHand().getTypeId() == 356) {
					if (event.getAction() == Action.RIGHT_CLICK_AIR
							|| event.getAction() == Action.RIGHT_CLICK_BLOCK) {
						//tp prevent placing:
						event.setCancelled(true);
						if (Rocket.getRockets(player).size() < plugin.rocketMatchLimit) {
							if (Rocket.getRockets(player).size() < plugin.rocketPlayerLimit) {
								player.playSound(player.getLocation(),
										Sound.SILVERFISH_IDLE, 100L, 1L);
								Fireball rocket = player
										.launchProjectile(Fireball.class);
								rocket.setShooter(player);
								rocket.setVelocity(player.getLocation()
										.getDirection().clone().normalize()
										.multiply(plugin.rocketSpeedMulti));
								new Rocket(player, rocket, plugin);
								ItemStack i = player.getItemInHand();
								if (i.getAmount() <= 1)
									player.setItemInHand(null);
								else {
									i.setAmount(i.getAmount() - 1);
									player.setItemInHand(i);
								}
							} else {
								player.sendMessage(plugin.t
										.getString("ROCKET_PLAYER_LIMIT_REACHED"));
							}
						} else {
							player.sendMessage(plugin.t
									.getString("ROCKET_MATCH_LIMIT_REACHED"));
						}
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onItemInHand(PlayerItemHeldEvent event) {
		final Player player = event.getPlayer();
		if (Lobby.getTeam(player) != null) {
			if (player.getInventory().getItem(event.getNewSlot()) != null
					&& player.getInventory().getItem(event.getNewSlot())
							.getTypeId() == 280) {
				if (!taskIds.containsKey(player)) {
					int taskId = plugin.getServer().getScheduler()
							.scheduleSyncRepeatingTask(plugin, new Runnable() {

								@Override
								public void run() {
									if (player.getItemInHand().getTypeId() == 280) {
										Block block = player.getTargetBlock(
												transparent, 1000);
										if (!Airstrike.isBlock(block, player)) {
											Airstrike.demark(player);
											Airstrike.mark(block, player);
										}
									} else {
										plugin.getServer()
												.getScheduler()
												.cancelTask(taskIds.get(player));
										taskIds.remove(player);
										Airstrike.demark(player);
									}
								}
							}, 0L, 1L);
					taskIds.put(player, taskId);
				}
			} else {
				if (taskIds.containsKey(player)) {
					plugin.getServer().getScheduler()
							.cancelTask(taskIds.get(player));
					taskIds.remove(player);
					Airstrike.demark(player);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onProjectileHit(ProjectileHitEvent event) {
		Projectile shot = event.getEntity();
		if (shot instanceof Snowball) {
			if (shot.getShooter() instanceof Player) {
				Player player = (Player) shot.getShooter();
				if (mm.getMatch(player) != null) {
					Match match = mm.getMatch(player);
					Location loc = shot.getLocation();
					// mine
					Block block = loc.getBlock();
					Mine mine = Mine.isMine(block);
					if (mine != null && match == mine.match && (match.enemys(player, mine.player) || player.equals(mine.player))) {
						mine.explode(true);
					}
					BlockIterator iterator = new BlockIterator(loc.getWorld(),
							loc.toVector(), shot.getVelocity().normalize(), 0,
							2);
					while (iterator.hasNext()) {
						Mine m = Mine.isMine(iterator.next());
						if (m != null && match == m.match && (match.enemys(player, m.player) || player.equals(mine.player))) {
							m.explode(true);
						}
					}
					// effect
					if (plugin.effects) {
						if (match.isBlue(player)) {
							loc.getWorld().playEffect(loc, Effect.POTION_BREAK,
									2);
						} else if (match.isRed(player)) {
							loc.getWorld().playEffect(loc, Effect.POTION_BREAK,
									1);
						}
					}
				}
			}
		} else if (shot instanceof Egg) {
			if (plugin.grenades) {
				Grenade.hit(shot, plugin);
			}
		} else if (shot instanceof Fireball) {
			if (plugin.rocket) {
				Rocket rocket = Rocket.isRocket((Fireball) shot);
				if (rocket != null)
					rocket.die();
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerDamage(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player) {
			Player target = (Player) event.getEntity();
			if (Lobby.getTeam(target) != null) {
				if (plugin.mm.getMatch(target) != null && plugin.damage
						&& Lobby.getTeam(target) != Lobby.SPECTATE
						&& plugin.mm.getMatch(target).isSurvivor(target)
						&& event.getCause() != DamageCause.ENTITY_ATTACK
						&& plugin.mm.getMatch(target).started) {
					if (target.getHealth() <= event.getDamage()) {
						event.setDamage(0);
						event.setCancelled(true);
						plugin.mm.getMatch(target).death(target);
					}
				} else {
					event.setDamage(0);
					event.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		if (Lobby.getTeam(player) != null) {
			if (!player.isOp() && !player.hasPermission("paintball.admin")) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		if (Lobby.getTeam(player) != null) {
			if (!player.isOp() && !player.hasPermission("paintball.admin")) {
				event.setCancelled(true);
			}
			final Block block = event.getBlockPlaced();
			Match m = plugin.mm.getMatch(player);
			if (m != null && m.isSurvivor(player)) {
				if (block.getType() == Material.PUMPKIN) {
					// turret:
					event.setCancelled(true);
					if (Turret.getTurrets(player).size() < plugin.turretMatchLimit) {
						if (Turret.getTurrets(player).size() < plugin.turretPlayerLimit) {
							Snowman snowman = (Snowman) block
									.getLocation()
									.getWorld()
									.spawnEntity(block.getLocation(),
											EntityType.SNOWMAN);
							new Turret(player, snowman,
									plugin.mm.getMatch(player), plugin);
							ItemStack i = player.getItemInHand();
							if (i.getAmount() <= 1)
								player.setItemInHand(null);
							else {
								i.setAmount(i.getAmount() - 1);
								player.setItemInHand(i);
							}
						} else {
							player.sendMessage(plugin.t
									.getString("TURRET_PLAYER_LIMIT_REACHED"));
						}
					} else {
						player.sendMessage(plugin.t
								.getString("TURRET_MATCH_LIMIT_REACHED"));
					}

				} else if (block.getType() == Material.FLOWER_POT) {
					// mine:
					event.setCancelled(true);
					if (Mine.getMines(player).size() < plugin.mineMatchLimit) {
						if (Mine.getMines(player).size() < plugin.minePlayerLimit) {
							plugin.getServer()
									.getScheduler()
									.scheduleSyncDelayedTask(this.plugin,
											new Runnable() {

												@Override
												public void run() {
													block.setType(Material.FLOWER_POT);
												}
											}, 1L);
							new Mine(player, block, plugin.mm.getMatch(player),
									plugin);
							ItemStack i = player.getItemInHand();
							if (i.getAmount() <= 1)
								player.setItemInHand(null);
							else {
								i.setAmount(i.getAmount() - 1);
								player.setItemInHand(i);
							}
						} else {
							player.sendMessage(plugin.t
									.getString("MINE_PLAYER_LIMIT_REACHED"));
						}
					} else {
						player.sendMessage(plugin.t
								.getString("MINE_MATCH_LIMIT_REACHED"));
					}

				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerHunger(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			if (Lobby.getTeam(player) != null) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerItemsI(PlayerPickupItemEvent event) {
		Player player = event.getPlayer();
		if (Lobby.getTeam(player) != null) {
			if (!player.isOp() && !player.hasPermission("paintball.admin"))
				event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerItemsII(PlayerDropItemEvent event) {
		Player player = event.getPlayer();
		if (Lobby.getTeam(player) != null) {
			if (!player.isOp() && !player.hasPermission("paintball.admin"))
				event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
		if (Lobby.getTeam(event.getPlayer()) != null
				&& !event.getMessage().startsWith("/pb")
				&& !isAllowedCommand(event.getMessage())) {
			if (!event.getPlayer().hasPermission("paintball.admin")
					&& !event.getPlayer().isOp()) {
				event.getPlayer().sendMessage(
						plugin.t.getString("COMMAND_NOT_ALLOWED"));
				event.setCancelled(true);
			}
		}
	}

	private boolean isAllowedCommand(String cmd) {
		String[] split = cmd.split(" ");
		if (plugin.allowedCommands.contains(cmd))
			return true;
		for (int i = 0; i < split.length; i++) {
			String cmds = "";
			for (int a = 0; a <= i; a++) {
				if (a == i)
					cmds += split[a];
				else
					cmds += split[a] + " ";
			}
			if (plugin.allowedCommands.contains(cmds)
					|| plugin.allowedCommands.contains(cmds + " *"))
				return true;
		}
		return false;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPbCommands(PlayerCommandPreprocessEvent event) {
		Player player = event.getPlayer();
		String[] m = event.getMessage().split(" ");
		// basic commands
		if (m[0].equalsIgnoreCase("/pb")) {
			if (m.length == 1) {
				plugin.cm.pbhelp(player);
			} else if (m[1].equalsIgnoreCase("help")
					|| m[1].equalsIgnoreCase("?")) {
				plugin.cm.pbhelp(player);
			} else if (m[1].equalsIgnoreCase("info")) {
				plugin.cm.pbinfo(player);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();

		if (Lobby.getTeam(player) != null) {
			if (plugin.chatnames) {
				ChatColor farbe = Lobby.LOBBY.color();
				if (Lobby.isPlaying(player) || Lobby.isSpectating(player)) {

					// Color:
					if (plugin.mm.getMatch(player).isRed(player))
						farbe = Lobby.RED.color();
					else if (plugin.mm.getMatch(player).isBlue(player))
						farbe = Lobby.BLUE.color();
					else if (plugin.mm.getMatch(player).isSpec(player))
						farbe = Lobby.SPECTATE.color();
				}
				event.setMessage(farbe + event.getMessage());
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDead(PlayerDeathEvent event) {
		Player player = (Player) event.getEntity();
		if (Lobby.getTeam(player) != null) {
			if (Lobby.isPlaying(player) || Lobby.isSpectating(player))
				mm.getMatch(player).left(player);
			plugin.leaveLobby(player, true, false, false);
			// drops?
			event.setDroppedExp(0);
			event.setKeepLevel(true);
			event.getDrops().removeAll(event.getDrops());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		final Player player = (Player) event.getPlayer();
		plugin.pm.addPlayer(player.getName());

		plugin.getServer().getScheduler()
				.scheduleSyncDelayedTask(plugin, new Runnable() {

					@Override
					public void run() {
						if (plugin.autoLobby && plugin.autoTeam)
							plugin.cm.joinTeam(player, Lobby.RANDOM);
						else if (plugin.autoLobby)
							plugin.cm.joinLobbyPre(player);
					}
				}, 1L);

	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerQuit(PlayerQuitEvent event) {
		this.onPlayerDisconnect(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerKick(PlayerKickEvent event) {
		// EVENT IS CANCELLED? => RETURN
		if (event.isCancelled())
			return;
		this.onPlayerDisconnect(event.getPlayer());
	}

	private void onPlayerDisconnect(final Player player) {
		if (Lobby.getTeam(player) != null) {
			// plugin.nf.leave(player.getName());
			// exit game
			if (Lobby.isPlaying(player) || Lobby.isSpectating(player))
				mm.getMatch(player).left(player);
			plugin.leaveLobby(player, true, true, true);
			// player.kickPlayer("You disconnected already.");

			/*
			 * //clear inventory plugin.clearInv(player); //Exit lobby
			 * Lobby.remove(player); //Teleport back
			 * player.teleport(plugin.pm.getLoc(player));
			 */

		}

	}

}
