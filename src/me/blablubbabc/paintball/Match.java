package me.blablubbabc.paintball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Match {

	private Paintball plugin;
	private HashMap<Player, Integer> livesLeft = new HashMap<Player, Integer>();
	private HashMap<Player, Integer> respawnsLeft = new HashMap<Player, Integer>();
	private ArrayList<Player> redT = new ArrayList<Player>();
	private ArrayList<Player> blueT = new ArrayList<Player>();
	//STATS
	private HashMap<String, Integer> shots = new HashMap<String, Integer>();
	private HashMap<String, Integer> hits = new HashMap<String, Integer>();
	private HashMap<String, Integer> kills = new HashMap<String, Integer>();
	private HashMap<String, Integer> deaths = new HashMap<String, Integer>();
	private HashMap<String, Integer> teamattacks = new HashMap<String, Integer>();
	private HashMap<String, Integer> grenades = new HashMap<String, Integer>();
	private HashMap<String, Integer> airstrikes = new HashMap<String, Integer>();

	//private ArrayList<Player> players = new ArrayList<Player>();
	private HashMap<String, Location> playersLoc = new HashMap<String, Location>();;
	private boolean matchOver = false;

	private Set<Player> spec;
	private String arena;

	private int startTaskId;
	private int startCount;

	public int roundTime;
	private int roundTimeTaskId;

	private int spawnBlue;
	private int spawnRed;
	private int spawnSpec;

	private int setting_balls;
	private int setting_grenades;
	private int setting_airstrikes;
	private int setting_lives;
	private int setting_respawns;
	private int setting_round_time;

	public boolean started = false;

	public ArrayList<Player> winners = new ArrayList<Player>();
	public ArrayList<Player> loosers = new ArrayList<Player>();
	public String win = "";
	public String loose = "";



	public Match(final Paintball plugin, Set<Player> red, Set<Player> blue, Set<Player> spec, Set<Player> random, String arena) {
		this.plugin = plugin;
		this.arena = arena;
		this.started = false;

		this.spawnBlue = 0;
		this.spawnRed = 0;
		this.spawnSpec = 0;

		this.setting_balls = plugin.balls;
		this.setting_grenades = plugin.grenadeAmount;
		this.setting_airstrikes = plugin.airstrikeAmount;
		this.setting_lives = plugin.lives;
		this.setting_respawns = plugin.respawns;
		this.setting_round_time = plugin.roundTimer;
		calculateSettings();

		//TEAMS
		for(Player p : red) {
			this.redT.add(p);
			//players.add(p);
		}
		for(Player p : blue) {
			this.blueT.add(p);
			//players.add(p);
		}
		this.spec = spec;

		//randoms:
		List<Player> rand = new ArrayList<Player>();
		for(Player p : random) {
			rand.add(p);
		}
		Collections.shuffle(rand);
		for(Player p : rand) {
			//players.add(p);
			if(this.blueT.size() < this.redT.size()){
				this.blueT.add(p);
			}
			else if(this.redT.size() <= this.blueT.size()){
				this.redT.add(p);
			}
		}

		for(Player p : getAllPlayer()) {
			//LIVES + RESPAWNS
			livesLeft.put(p,setting_lives);
			respawnsLeft.put(p, setting_respawns);
			//STATS
			this.shots.put(p.getName(), 0);
			this.hits.put(p.getName(), 0);
			this.kills.put(p.getName(), 0);
			this.deaths.put(p.getName(), 0);
			this.teamattacks.put(p.getName(), 0);
			this.grenades.put(p.getName(), 0);
			this.airstrikes.put(p.getName(), 0);

			plugin.checks(p);
			spawnPlayer(p);
		}

		for(Player p : this.spec) {
			plugin.checks(p);
			spawnSpec(p);
		}
		//colorchanges:
		changeAllColors();

		//WAITING TIMER:
		this.started = false;
		startCount = plugin.countdownStart;

		startTaskId = plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, new Runnable() {

			@Override
			public void run() {
				if( startCount == plugin.countdownStart && startCount > 0) {
					sendCountdown(startCount);
					startCount--;
					return;
				}
				if(( startCount % 10 ) == 0 && startCount > 3 )
				{
					//if above 3 and divisable by 10 message here
					sendCountdown(startCount);
				}

				if( startCount < 4 && startCount > 0)
				{
					//if below 4 message here (regardless of divisibility)
					sendCountdown(startCount);
				}
				startCount--;
				if( startCount < 1) {
					plugin.getServer().getScheduler().cancelTask(startTaskId);
					//START:
					started = true;
					//lives + start!:
					HashMap<String, String> vars = new HashMap<String, String>();
					vars.put("lives", String.valueOf(setting_lives));
					if(setting_respawns == -1) vars.put("respawns", plugin.t.getString("INFINITE"));
					else vars.put("respawns", String.valueOf(setting_respawns));
					vars.put("round_time", String.valueOf(setting_round_time));

					plugin.nf.status(plugin.t.getString("MATCH_SETTINGS_INFO", vars));
					plugin.nf.status(plugin.t.getString("MATCH_START"));

					makeAllVisible();
					startRoundTimer();
				}
			}
		}, 0L, 20L);
	}

	public void endSchedulers() {
		if(plugin.getServer().getScheduler().isCurrentlyRunning(startTaskId) || plugin.getServer().getScheduler().isQueued(startTaskId)) plugin.getServer().getScheduler().cancelTask(startTaskId);
		if(plugin.getServer().getScheduler().isCurrentlyRunning(roundTimeTaskId) || plugin.getServer().getScheduler().isQueued(roundTimeTaskId)) plugin.getServer().getScheduler().cancelTask(roundTimeTaskId);
	}

	public synchronized int getRoundTime() {
		return roundTime;
	}

	private synchronized void minusRoundTime() {
		roundTime--;
	}

	private synchronized void startRoundTimer() {
		roundTime = setting_round_time;

		roundTimeTaskId = plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, new Runnable() {

			@Override
			public void run() {
				int time = getRoundTime();
				if(time == setting_round_time) {
					minusRoundTime();
					return;
				}
				if(( time % 10 ) == 0 && time > 5 )
				{
					//if above 5 and divisable by 10 message here
					sendRoundTime(time);
				}

				if( time < 6 && time > 0)
				{
					//if below 6 message here (regardless of divisibility)
					sendRoundTime(time);
				}
				minusRoundTime();
				if( time < 1) {
					plugin.getServer().getScheduler().cancelTask(roundTimeTaskId);
					//END:
					if(matchOver) return;
					matchOver = true;
					//winner?

				}
			}
		}, 0L, 20L);
	}

	private void sendRoundTime(int time) {
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("seconds", String.valueOf(time));
		for(Player player : getAll()) {
			player.sendMessage(plugin.t.getString("MATCH_REMAINING_TIME", vars));
		}
	}

	private void sendCountdown(int counter) {
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("seconds", String.valueOf(counter));
		for(Player player : getAll()) {
			player.sendMessage(plugin.t.getString("COUNTDOWN", vars));
		}
	}

	//SPAWNS

	public void spawnPlayer(Player player) {
		if(redT.contains(player)) {
			ArrayList<Location> redspawns = plugin.am.getRedSpawns(arena);
			if(spawnRed > (redspawns.size()-1)) spawnRed = 0;
			Location loc = redspawns.get(spawnRed);
			player.teleport(loc);
			spawnRed++;
			//afk Location
			playersLoc.put(player.getName(), loc);
		} else if(blueT.contains(player)) {
			ArrayList<Location> bluespawns = plugin.am.getBlueSpawns(arena);
			if(spawnBlue > (bluespawns.size()-1)) spawnBlue = 0;
			Location loc = bluespawns.get(spawnBlue);
			player.teleport(loc);
			spawnBlue++;
			//afk Location
			playersLoc.put(player.getName(), loc);
		} else {
			return;
		}
		//INVENTORY
		plugin.clearInv(player);

		player.getInventory().setHelmet(Lobby.getTeam(getTeamName(player)).helmet());
		if(setting_balls > 0 ) player.getInventory().addItem(new ItemStack(Material.SNOW_BALL, setting_balls));
		else if(setting_balls == -1 ) player.getInventory().addItem(new ItemStack(Material.SNOW_BALL, 10));
		if(setting_grenades > 0 ) player.getInventory().addItem(new ItemStack(Material.EGG, setting_grenades));
		else if(setting_grenades == -1 ) player.getInventory().addItem(new ItemStack(Material.EGG, 10));
		if(setting_airstrikes > 0 ) player.getInventory().addItem(new ItemStack(Material.STICK, setting_airstrikes));
		else if(setting_airstrikes == -1 ) player.getInventory().addItem(new ItemStack(Material.STICK, 10));
		//MESSAGE
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("team_color", Lobby.getTeam(getTeamName(player)).color().toString());
		vars.put("team", getTeamName(player));
		player.sendMessage(plugin.t.getString("BE_IN_TEAM", vars));
	}

	public void spawnSpec(Player player) {
		ArrayList<Location> specspawns = plugin.am.getSpecSpawns(arena);
		if(spawnSpec > (specspawns.size()-1)) spawnSpec = 0;
		player.teleport(specspawns.get(spawnSpec));
		spawnSpec++;
		//INVENTORY
		player.getInventory().setHelmet(Lobby.SPECTATE.helmet());
		//MESSAGE
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("team_color", Lobby.getTeam(player).color().toString());
		vars.put("team", Lobby.getTeam(player).getName());
		player.sendMessage(plugin.t.getString("BE_SPECTATOR", vars));
	}

	//INVENTORY
	private void calculateSettings() {
		HashMap<String, Integer> settings = plugin.am.getArenaSettings(arena);
		//BALLS
		setting_balls += settings.get("balls");
		if(setting_balls < -1) setting_balls = -1;
		//GRENADES
		setting_grenades += settings.get("grenades");
		if(setting_grenades < -1) setting_grenades = -1;
		//AIRSTRIKES
		setting_airstrikes += settings.get("airstrikes");
		if(setting_airstrikes < -1) setting_airstrikes = -1;
		//LIVES
		setting_lives += settings.get("lives");
		if(setting_lives < 1) setting_lives = 1;
		//RESPAWNS
		setting_respawns += settings.get("respawns");
		if(setting_respawns < -1) setting_respawns = -1;
		//ROUND TIME
		setting_round_time += settings.get("round_time");
		if(setting_round_time < 30) setting_round_time = 30;
	}

	public void makeAllVisible() {
		for(Player pl : getAll()) {
			for(Player p : getAll()) {
				if(!p.equals(pl)) pl.showPlayer(p);
			}	
		}
	}

	public void changeAllColors() {
		for(Player p : redT) {
			//chatnames
			String n = plugin.red+p.getName();
			if(n.length() > 16) n = (String) n.subSequence(0, n.length() - (n.length()-16));
			/*if(plugin.chatnames) {
				p.setDisplayName(n+white);
			}*/
			//listnames
			if(plugin.listnames) {
				p.setPlayerListName(n);
			}
		}
		for(Player p : blueT) {
			//chatnames
			String n = plugin.blue+p.getName();
			if(n.length() > 16) n = (String) n.subSequence(0, n.length() - (n.length()-16));
			/*if(plugin.chatnames) {
				p.setDisplayName(n+white);
			}*/
			//listnames
			if(plugin.listnames) {
				p.setPlayerListName(n);
			}
		}
	}

	public void undoAllColors() {
		for(Player p : getAllPlayer()) {
			/*if(plugin.chatnames) {
				p.setDisplayName(p.getName());
			}*/
			//listnames
			if(plugin.listnames) {
				if(Lobby.isPlaying(p)) p.setPlayerListName(null);
			}
		}
	}

	public int teamSizeRed() {
		return redT.size();
	}
	public int teamSizeBlue() {
		return blueT.size();
	}

	public String getArena() {
		return this.arena;
	}

	public synchronized int survivors(ArrayList<Player> team) {
		int survivors = 0;
		for(Player p : team) {
			if(isSurvivor(p)) {
				survivors++;
			}
		}
		return survivors;
	}

	public synchronized boolean isSurvivor(Player player) {
		if(spec.contains(player)) return true;
		if(getTeam(player) != null) {
			if(setting_respawns == -1) return true;
			if(respawnsLeft.get(player) > 0) return true;
			if(livesLeft.get(player) > 0) return true;
		}
		return false;
	}

	public String getTeamName(Player player) {
		if(redT.contains(player)) return Lobby.RED.getName();
		if(blueT.contains(player)) return Lobby.BLUE.getName();
		return null;
	}
	public String getEnemyTeamName(Player player) {
		if(redT.contains(player)) return Lobby.BLUE.getName();
		if(blueT.contains(player)) return Lobby.RED.getName();
		return null;
	}

	public boolean isSpec(Player player) {
		if(spec.contains(player)) return true;
		else return false;
	}
	public boolean isRed(Player player) {
		if(redT.contains(player)) return true;
		else return false;
	}
	public boolean isBlue(Player player) {
		if(blueT.contains(player)) return true;
		else return false;
	}

	public ArrayList<Player> getTeam(Player player) {
		if(redT.contains(player)) return redT;
		if(blueT.contains(player)) return blueT;
		return null;
	}
	public ArrayList<Player> getEnemyTeam(Player player) {
		if(redT.contains(player)) return blueT;
		if(blueT.contains(player)) return redT;
		return null;
	}

	public boolean inMatch(Player player) {
		if(redT.contains(player)) return true;
		if(blueT.contains(player)) return true;
		if(spec.contains(player)) return true;
		return false;
	}

	public boolean enemys(Player player1, Player player2) {
		if(redT.contains(player1) && blueT.contains(player2)) return true;
		if(redT.contains(player2) && blueT.contains(player1)) return true;
		return false;
	}

	public boolean friendly(Player player1, Player player2) {
		if(redT.contains(player1) && redT.contains(player2)) return true;
		if(blueT.contains(player1) && blueT.contains(player2)) return true;
		return false;
	}

	public ArrayList<Player> getAllPlayer() {
		ArrayList<Player> players = new ArrayList<Player>();
		players.addAll(redT);
		players.addAll(blueT);
		return players;
	}

	public ArrayList<Player> getAllSpec() {
		ArrayList<Player> list = new ArrayList<Player>();
		for(Player p : spec) {
			list.add(p);
		}
		return list;
	}

	public ArrayList<Player> getAll() {
		//return players;
		ArrayList<Player> list = new ArrayList<Player>(getAllPlayer());
		for(Player p : spec) {
			list.add(p);
		}
		return list;
	}

	//AKTIONS

	public synchronized void left(Player player) {
		//team?
		if(getTeam(player) != null) {
			//0 leben aka tot
			livesLeft.put(player, 0);
			respawnsLeft.put(player, 0);
			//afk detection-> remove player
			if(plugin.afkDetection) {
				plugin.afkMatchCount.remove(player);
			}
			//survivors?->endGame
			//math over already?
			if(matchOver) return;
			if(survivors(getTeam(player)) == 0) {
				gameEnd(false, getEnemyTeam(player), getTeam(player), getEnemyTeamName(player), getTeamName(player));
			}
		} else if(spec.contains(player)) spec.remove(player);
	}

	private synchronized void respawn(Player player) {
		if(respawnsLeft.get(player) != 0) {
			livesLeft.put(player, setting_lives);
			if(setting_respawns != -1) respawnsLeft.put(player, respawnsLeft.get(player)-1);
			//message
			HashMap<String, String> vars = new HashMap<String, String>();
			vars.put("lives", String.valueOf(setting_lives));
			if(setting_respawns == -1) vars.put("respawns", plugin.t.getString("INFINITE"));
			else vars.put("respawns", String.valueOf(setting_respawns));
			plugin.t.getString("RESPAWN", vars);

		} else {
			//dead
			plugin.joinLobby(player);
		}
	}

	public synchronized void shot(Player player) {
		//add 1
		shots.put(player.getName(), shots.get(player.getName())+1);
	}
	public synchronized void grenade(Player player) {
		//add 1
		grenades.put(player.getName(), grenades.get(player.getName())+1);
	}
	public synchronized void airstrike(Player player) {
		//add 1
		airstrikes.put(player.getName(), airstrikes.get(player.getName())+1);
	}

	public synchronized void hitSnow(Player target, Player shooter) {
		//math over already?
		if(matchOver) return;

		//target already dead?
		if(livesLeft.get(target) <= 0) return;
		//Teams?
		if(enemys(target, shooter)) {
			//player not dead already?
			if(livesLeft.get(target) > 0) {
				//-1 live
				livesLeft.put(target, livesLeft.get(target)-1);
				//stats
				hits.put(shooter.getName(), hits.get(shooter.getName())+1);
				//dead?->frag
				//message:
				if(livesLeft.get(target) <= 0) {
					frag(target, shooter);
				} else {
					shooter.sendMessage(plugin.t.getString("YOU_HIT"));
					target.sendMessage(plugin.t.getString("YOU_WERE_HIT"));
				}
			}
		} else if(friendly(target, shooter)) {
			//message
			//-points
			teamattacks.put(shooter.getName(), teamattacks.get(shooter.getName())+1);
			if(plugin.pointsPerTeamattack != 0) {
				HashMap<String,String> vars = new HashMap<String, String>();
				vars.put("points", String.valueOf(plugin.pointsPerTeamattack));
				shooter.sendMessage(plugin.t.getString("YOU_HIT_MATE_POINTS", vars));
			} else {
				shooter.sendMessage(plugin.t.getString("YOU_HIT_MATE"));
			}
		}
	}

	public synchronized void frag(final Player target, final Player killer) {
		//math over already?
		if(matchOver) return;

		final Match this2 = this;
		//STATS
		deaths.put(target.getName(), deaths.get(target.getName())+1);
		kills.put(killer.getName(), kills.get(killer.getName())+1);

		//feed
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("target", target.getName());
		vars.put("killer", killer.getName());
		vars.put("points", String.valueOf(plugin.pointsPerKill));
		vars.put("money", String.valueOf(plugin.cashPerKill));
		killer.sendMessage(plugin.t.getString("YOU_KILLED", vars));
		target.sendMessage(plugin.t.getString("YOU_WERE_KILLED", vars));
		plugin.nf.feed(target, killer, this2);

		if(survivors(getTeam(target)) == 0) {
			matchOver = true;
		}

		//afk detection on frag
		if(plugin.afkDetection) {
			if(target.getLocation().getWorld().equals(playersLoc.get(target.getName()).getWorld()) && target.getLocation().distance(playersLoc.get(target.getName())) <= plugin.afkRadius && shots.get(target) == 0 && kills.get(target) == 0) {
				int afkCount;
				if(plugin.afkMatchCount.get(target.getName()) != null) {
					afkCount = plugin.afkMatchCount.get(target.getName());
				} else {
					afkCount = 0;
				}
				plugin.afkMatchCount.put(target.getName(), afkCount+1);
			}else {
				plugin.afkMatchCount.remove(target.getName());
			}
		}

		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

			@Override
			public void run() {
				if(isSurvivor(target)) {
					//afk check
					if(plugin.afkDetection) {
						String name = target.getName();
						if(plugin.afkMatchCount.get(name) >= plugin.afkMatchAmount){
							//consequences after being afk:
							plugin.afkMatchCount.remove(name);
							respawnsLeft.put(target, 0);
							plugin.joinLobby(target);

							Lobby.getTeam(target).removeMember(target);
							plugin.nf.afkLeave(target, this2);
							target.sendMessage(plugin.t.getString("YOU_LEFT_TEAM"));
						}
					} else respawn(target);
				}
				else plugin.joinLobby(target);
				//survivors?->endGame
				if(survivors(getTeam(target)) == 0) {
					gameEnd(false, getTeam(killer), getTeam(target), getTeamName(killer), getTeamName(target));
				}
			}
		}, 1L);

	}

	public synchronized void death(final Player target) {
		//math over already?
		if(matchOver) return;

		//afk detection on death
		if(plugin.afkDetection) {
			String name = target.getName();
			if(target.getLocation().getWorld().equals(playersLoc.get(name).getWorld()) && target.getLocation().distance(playersLoc.get(name)) <= plugin.afkRadius && shots.get(target) == 0 && kills.get(target) == 0) {
				int afkCount;
				if(plugin.afkMatchCount.get(name) != null) {
					afkCount = plugin.afkMatchCount.get(name);
				} else {
					afkCount = 0;
				}
				//consequences:
				if((afkCount+1) >= plugin.afkMatchAmount){
					//consequences after being afk:
					plugin.afkMatchCount.remove(name);
					respawnsLeft.put(target, 0);
					plugin.joinLobby(target);

					Lobby.getTeam(target).removeMember(target);
					plugin.nf.afkLeave(target, this);
					target.sendMessage(plugin.t.getString("YOU_LEFT_TEAM"));
				}else {
					plugin.afkMatchCount.put(target.getName(), afkCount+1);
				}
			}else {
				plugin.afkMatchCount.remove(target.getName());
			}
		}

		//feed
		target.sendMessage(plugin.t.getString("YOU_DIED"));
		plugin.nf.death(target, this);
		//points+cash+kill+death
		deaths.put(target.getName(), (deaths.get(target.getName())+1));
		//survivors?->endGame
		//0 leben aka tot
		livesLeft.put(target, 0);

		if(isSurvivor(target)) respawn(target);
		else plugin.joinLobby(target);
		//survivors?->endGame
		if(survivors(getTeam(target)) == 0) {
			gameEnd(false, getEnemyTeam(target), getTeam(target), getEnemyTeamName(target), getTeamName(target));
		}
	}

	private synchronized void gameEnd(boolean draw, ArrayList<Player> winnerS, ArrayList<Player> looserS, String winS, String looseS) {
		matchOver = true;
		endSchedulers();
		undoAllColors();
		if(!draw) {
			for(Player p : winnerS) {
				this.winners.add(p);
			}
			for(Player p : looserS) {
				this.loosers.add(p);
			}
			this.win = winS;
			this.loose = looseS;
		}
		plugin.mm.gameEnd(this, draw, playersLoc, spec, shots, hits, kills, deaths, teamattacks, grenades, airstrikes);
	}

}
