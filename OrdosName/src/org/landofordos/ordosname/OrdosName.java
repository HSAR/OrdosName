package org.landofordos.ordosname;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
//import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class OrdosName extends JavaPlugin implements Listener {

	// Important plugin objects
	private static Server server;
	private static Logger logger;
	// sql vars
	private String URL;
	private String dbUser;
	private String dbPass;
	private Connection connection;
	//
	private boolean verbose;
	private long dbcleanuptime;

	public void onDisable() {
		logger.info("Disabled.");
	}

	public void onEnable() {
		// static reference to this plugin and the server
		// plugin = this;
		server = getServer();
		// start the logger
		logger = getLogger();
		// save config to default location if not already there
		this.saveDefaultConfig();
		// verbose logging? retrieve value from config file.
		verbose = this.getConfig().getBoolean("verboselogging");
		if (verbose) {
			logger.info("Verbose logging enabled.");
		} else {
			logger.info("Verbose logging disabled.");
		}
		// retrieve SQL variables from config
		URL = this.getConfig().getString("URL");
		dbUser = this.getConfig().getString("Username");
		dbPass = this.getConfig().getString("Password");
		// create database connection
		try {
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection("jdbc:" + URL + "?user=" + dbUser + "&password=" + dbPass);
		} catch (SQLException e1) {
			e1.printStackTrace();
		} catch (ClassNotFoundException e2) {
			e2.printStackTrace();
		}
		// register events
		server.getPluginManager().registerEvents(this, this);
		// first-run initialisation
		final boolean firstrun = this.getConfig().getBoolean("firstrun");
		if (firstrun) {
			try {
				boolean SQLsuccess = this.createSQL();
				if (verbose && SQLsuccess) {
					logger.info("Tables created successfully.");
				}
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			this.getConfig().set("firstrun", false);
			this.saveConfig();
			if (verbose) {
				logger.info("First-run initialisation complete.");
			}
		}
		// retrieve database cleanup threshold from config
		dbcleanuptime = this.getConfig().getLong("dbcleanuptime");

	}

	private boolean createSQL() throws SQLException, ClassNotFoundException {
		Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		try {
			statement.executeUpdate("CREATE TABLE ordosname (user VARCHAR( 32 )  NOT NULL UNIQUE PRIMARY KEY, first VARCHAR( 32 ), "
					+ "last VARCHAR( 32 ), title VARCHAR( 32 ), suffix VARCHAR( 32 ), titleoverridesfirst BIT DEFAULT FALSE, "
					+ "enabled BIT DEFAULT TRUE, displayname VARCHAR( 128 ), lastseen DATETIME NOT NULL);");
		} catch (SQLException e) {
			logger.info(" SQL Exception: " + e);
			return false;
		}
		return true;
	}

	private int getResultSetNumRows(ResultSet res) {
		try {
			// get row at beginning so as to not affect it
			int originalPlace = res.getRow();
			res.last();
			// Get the row number of the last row which is also the row count
			int rowCount = res.getRow();
			// move row back to original position
			res.absolute(originalPlace);
			return rowCount;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return -1;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		// get timestamp for DB inserts
		Object timestamp = new java.sql.Timestamp((new Date()).getTime());
		// String timestamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		// command functionality
		// ------------- ordosname functionality
		if (cmd.getName().equalsIgnoreCase("ordosname")) {
			if ((args.length < 1) || ((args.length == 1) && (args[0].equalsIgnoreCase("help")))) {
				sender.sendMessage(ChatColor.YELLOW + "/setfirstname [name] " + ChatColor.WHITE + "- " + ChatColor.DARK_GREEN + "Set first name");
				sender.sendMessage(ChatColor.YELLOW + "/setlastname [name] " + ChatColor.WHITE + "- " + ChatColor.DARK_GREEN + "Set last name");
				sender.sendMessage(ChatColor.YELLOW + "/settitle [name] " + ChatColor.WHITE + "- " + ChatColor.DARK_GREEN + "Set title");
				sender.sendMessage(ChatColor.YELLOW + "/setsuffix [name] " + ChatColor.WHITE + "- " + ChatColor.DARK_GREEN + "Set suffix");
				sender.sendMessage(ChatColor.YELLOW + "/namereload ");
				return false;
			}
			// code to reload configuration
			if ((args.length == 1) && (args[0].equalsIgnoreCase("reload")) && (sender.hasPermission("ordosname.admin.reloadconfig"))) {
				logger.info(sender.getName() + " initiated configuration reload.");
				// check for changes in the verbose logging var
				if (verbose != this.getConfig().getBoolean("verbose")) {
					verbose = this.getConfig().getBoolean("verboselogging");
					if (verbose) {
						logger.info("Verbose logging enabled.");
					} else {
						logger.info("Verbose logging disabled.");
					}
				}
				// retrieve database cleanup threshold from config, if it has changed
				if (dbcleanuptime != this.getConfig().getLong("dbcleanuptime")) {
					dbcleanuptime = this.getConfig().getLong("dbcleanuptime");
					// immediately run database cleanup using new threshold
					logger.info("New database cleanup threshold (" + dbcleanuptime + ") loaded from config.");
					dbcleanup();
				}
				// TODO: Add Towny integration here!
			}
			// code to check people's names
			if ((args.length > 1) && (args[0].equalsIgnoreCase("namecheck")) && (sender.hasPermission("ordosname.admin.namecheck"))) {
				// pick up spaced parameters held together by speech marks
				String nameToCheck = "";
				String target = null;
				// boolean object - false represents nameToCheck not started, true nameToCheck in progress, null nameToCheck ended
				Boolean nameToCheckstarted = false;
				for (int i = 0; i < args.length; i++) {
					if (target == null) {
						if (args[i].startsWith("\"")) {
							nameToCheckstarted = true;
						}
						if (nameToCheckstarted == true) {
							nameToCheck += " " + args[i];
							if (args[i].endsWith("\"")) {
								nameToCheckstarted = null;
							}
						}
					}
				}
				if (nameToCheckstarted == null) {
					// trim off the start and end speech marks
					nameToCheck = nameToCheck.substring(2, nameToCheck.length() - 1);
				} else {
					// if the nameToCheck never ENDED, that's bad news.
					// assume all is well, though, and just chop off the start.
					if (nameToCheckstarted == true) {
						nameToCheck = nameToCheck.substring(2, nameToCheck.length());
					}
				}
				// if the nameToCheck never started, assume single word nameToCheck
				if (!nameToCheckstarted) {
					nameToCheck = args[0];
				}
				if (server.getPlayer(nameToCheck) != null) {
					// the server returns a player object when queried
					Player player = server.getPlayer(nameToCheck);
					Statement statement;
					try {
						statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
						ResultSet RS = statement.executeQuery("SELECT * FROM ordosname WHERE user = '" + player.getName() + "';");
						if (!(RS == null) && (RS.first())) {
							if (!(RS.getBoolean("enabled"))) {
								if (verbose) {
									sender.sendMessage("");
								}
							} else {
								// if there's a result, set the player's name appropriately.
								// fetch name objects and append appropriate spacing
								String title = RS.getString("title");
								String last = RS.getString("last");
								String suffix = RS.getString("suffix");
								String first;
								// does the player's title override their first name, and do they have a title?
								if ((title != null) && (RS.getBoolean("titleoverridesfirst"))) {
									// if so, we won't be needing firstname.
									first = null;
								} else {
									// if not, we'll need to fetch their first name
									first = RS.getString("first");
								}
								// string of final name to be set
								String name = "";
								if (title != null) {
									name += title + " ";
								}
								if (first != null) {
									name += first + " ";
								}
								if (last != null) {
									name += last + " ";
								}
								if (suffix != null) {
									name += suffix;
								}
								if (name.endsWith(" ")) {
									name = name.substring(0, name.length() - 1);
								}
								if (name.length() > 0) {
									sender.sendMessage(ChatColor.GREEN + "The name of user " + ChatColor.WHITE + args[2] + ChatColor.GREEN + " is "
											+ ChatColor.WHITE + name);
								}
							}
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				} else {
					// if the server returned null for that player, try searching it as nickname instead.
					try {
						Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
						ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE displayname = '" + nameToCheck + "';");
						if (!(tryRS == null) && (tryRS.first())) {
							sender.sendMessage(ChatColor.GREEN + "The username of " + ChatColor.WHITE + nameToCheck + ChatColor.GREEN + " is "
									+ ChatColor.WHITE + tryRS.getString("displayname"));
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			}
		}
		// -------------
		if (cmd.getName().equalsIgnoreCase("namereload")) {
			if (args.length < 1) {
				sender.sendMessage(ChatColor.RED + "Incorrect number of arguments specified!");
				return false;
			} else {
				if (!(sender instanceof Player)) {
					sender.sendMessage("You cannot do this since you are not a player.");
				} else {
					if ((args.length == 0) || ((sender.getName().equals(args[0])) && (sender.hasPermission("ordosname.reload.self")))) {
						reloadPlayerName(sender, sender.getName());
						return true;
					}
				}
				if ((sender.hasPermission("ordosname.reload.others")) && (args.length == 1)) {
					reloadPlayerName(sender, args[0]);
					return true;
				}
				return false;
			}
		}
		// -------------
		if (cmd.getName().equalsIgnoreCase("setfirstname")) {
			if (args.length < 1) {
				if (!(sender instanceof Player)) {
					// if there are 0 arguments, clear the title of yourself.
					// this only works if you are a player
					sender.sendMessage("You cannot do this since you are not a player.");
					return false;
				} else {
					// however if it was a player that issued the command, execute it.
					// permissions check
					if (sender.hasPermission("ordosname.name.first.self")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET first = NULL, lastseen = '" + timestamp + "' WHERE user= '"
										+ sender.getName() + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record
								statement.executeUpdate("INSERT INTO ordosname (user, first, lastseen) VALUES ('" + sender.getName()
										+ "', NULL, FALSE, '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
				return false;
			} else {
				if (args.length == 1) {
					if (!(sender instanceof Player)) {
						// if only one arg specified take target to be self, but this only works if you are a player
						sender.sendMessage("You cannot do this since you are not a player.");
						return false;
					} else {
						// however if it was a player that issued the command, execute it.
						// permissions check
						if (sender.hasPermission("ordosname.name.first.self")) {
							try {
								Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
								ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
								if (!(tryRS == null) && (tryRS.first())) {
									// if there's a result, update the table instead of inserting.
									statement.executeUpdate("UPDATE ordosname SET first = '" + args[0] + "', lastseen = '" + timestamp
											+ "' WHERE user= '" + sender.getName() + "';");
								} else {
									// If no result was returned then the user has not been added before.
									// Use INSERT instead of update to create the record.
									statement.executeUpdate("INSERT INTO ordosname (user, first, lastseen) VALUES ('" + sender.getName() + "', '"
											+ args[0] + "', '" + timestamp + "');");
								}
								if (statement != null) {
									statement.close();
								}
							} catch (SQLException e) {
								e.printStackTrace();
							}
							reloadPlayerName((Player) sender);
							return true;
						} else {
							// "You don't have permission!"
						}
					}
				}
				if (args.length == 2) {
					// permission check
					if (sender.hasPermission("ordosname.name.first.others")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + args[1] + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET first = '" + args[0] + "', lastseen = '" + timestamp
										+ "' WHERE user= '" + args[1] + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record.
								statement.executeUpdate("INSERT INTO ordosname (user, first, lastseen) VALUES ('" + args[1] + "', '" + args[0]
										+ "', '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
			}
		}
		// -------------
		if (cmd.getName().equalsIgnoreCase("setlastname")) {
			if (args.length < 1) {
				if (!(sender instanceof Player)) {
					// if there are 0 arguments, clear the title of yourself.
					// this only works if you are a player
					sender.sendMessage("You cannot do this since you are not a player.");
					return false;
				} else {
					// however if it was a player that issued the command, execute it.
					// permissions check
					if (sender.hasPermission("ordosname.name.last.self")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET last = NULL, lastseen = '" + timestamp + "' WHERE user= '"
										+ sender.getName() + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record
								statement.executeUpdate("INSERT INTO ordosname (user, last, lastseen) VALUES ('" + sender.getName()
										+ "', NULL, FALSE, '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
				return false;
			} else {
				if (args.length == 1) {
					if (!(sender instanceof Player)) {
						// if only one arg specified take target to be self, but this only works if you are a player
						sender.sendMessage("You cannot do this since you are not a player.");
						return false;
					} else {
						// however if it was a player that issued the command, execute it.
						// permissions check
						if (sender.hasPermission("ordosname.name.last.self")) {
							try {
								Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
								ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
								if (!(tryRS == null) && (tryRS.first())) {
									// if there's a result, update the table instead of inserting.
									statement.executeUpdate("UPDATE ordosname SET last = '" + args[0] + "', lastseen = '" + timestamp
											+ "' WHERE user= '" + sender.getName() + "';");
								} else {
									// If no result was returned then the user has not been added before.
									// Use INSERT instead of update to create the record.
									statement.executeUpdate("INSERT INTO ordosname (user, last, lastseen) VALUES ('" + sender.getName() + "', '"
											+ args[0] + "', '" + timestamp + "');");
								}
								if (statement != null) {
									statement.close();
								}
							} catch (SQLException e) {
								e.printStackTrace();
							}
							reloadPlayerName((Player) sender);
							return true;
						} else {
							// "You don't have permission!"
						}
					}
				}
				if (args.length == 2) {
					// permission check
					if (sender.hasPermission("ordosname.name.last.others")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + args[1] + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET last = '" + args[0] + "', lastseen = '" + timestamp + "' WHERE user= '"
										+ args[1] + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record.
								statement.executeUpdate("INSERT INTO ordosname (user, last, lastseen) VALUES ('" + args[1] + "', '" + args[0]
										+ "', '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
			}
		}
		// -------------
		if (cmd.getName().equalsIgnoreCase("settitle")) {
			if (args.length < 1) {
				if (!(sender instanceof Player)) {
					// if there are 0 arguments, clear the title of yourself.
					// this only works if you are a player
					sender.sendMessage("You cannot do this since you are not a player.");
					return false;
				} else {
					// however if it was a player that issued the command, execute it.
					// permissions check
					if (sender.hasPermission("ordosname.name.title.self")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET title = NULL, titleoverridesfirst = FALSE, lastseen = '" + timestamp
										+ "' WHERE user= '" + sender.getName() + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record
								statement.executeUpdate("INSERT INTO ordosname (user, title, titleoverridesfirst, lastseen) VALUES ('"
										+ sender.getName() + "', NULL, FALSE, '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
				return false;
			} else {
				// does the title override the first name?
				boolean overridefirst = false;
				// if no override specified (i.e. of args.length == 1) leave it as the default, which is FALSE.
				// otherwise parse the override
				if (args.length >= 2) {
					overridefirst = Boolean.parseBoolean(args[1]);
				}
				if (args.length < 3) {
					if (!(sender instanceof Player)) {
						// if no target specified take target to be self, but this only works if you are a player
						sender.sendMessage("You cannot do this since you are not a player.");
						return false;
					} else {
						// however if it was a player that issued the command, execute it.
						// permissions check
						if (sender.hasPermission("ordosname.name.title.self")) {
							try {
								Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
								ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
								if (!(tryRS == null) && (tryRS.first())) {
									// if there's a result, update the table instead of inserting.
									statement.executeUpdate("UPDATE ordosname SET title = '" + args[0] + "', titleoverridesfirst = " + overridefirst
											+ ", lastseen = '" + timestamp + "' WHERE user= '" + sender.getName() + "';");
								} else {
									// If no result was returned then the user has not been added before.
									// Use INSERT instead of update to create the record.
									statement.executeUpdate("INSERT INTO ordosname (user, title, titleoverridesfirst, last, lastseen) VALUES ('"
											+ sender.getName() + "', '" + args[0] + "', " + overridefirst + ", '" + sender.getName() + "', '"
											+ timestamp + "');");
								}
								if (statement != null) {
									statement.close();
								}
							} catch (SQLException e) {
								e.printStackTrace();
							}
							reloadPlayerName((Player) sender);
							return true;
						} else {
							// "You don't have permission!"
						}
					}
				}
				if (args.length == 3) {
					// permission check
					if (sender.hasPermission("ordosname.name.title.others")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + args[2] + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET title = '" + args[0] + "', titleoverridesfirst = " + overridefirst
										+ ", lastseen = '" + timestamp + "' WHERE user= '" + args[2] + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record.
								statement.executeUpdate("INSERT INTO ordosname (user, title, titleoverridesfirst, last, lastseen) VALUES ('"
										+ args[2] + "', '" + args[0] + "', " + overridefirst + ", '" + args[2] + ", '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
			}
		}
		// -------------
		if (cmd.getName().equalsIgnoreCase("setsuffix")) {
			if (args.length < 1) {
				if (!(sender instanceof Player)) {
					// if there are 0 arguments, clear the title of yourself.
					// this only works if you are a player
					sender.sendMessage("You cannot do this since you are not a player.");
					return false;
				} else {
					// however if it was a player that issued the command, execute it.
					// permissions check
					if (sender.hasPermission("ordosname.name.suffix.self")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET suffix = NULL, lastseen = '" + timestamp + "' WHERE user= '"
										+ sender.getName() + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record
								statement.executeUpdate("INSERT INTO ordosname (user, suffix, lastseen) VALUES ('" + sender.getName()
										+ "', NULL, FALSE, '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
				return false;
			} else {
				String suffix = "";
				String target = null;
				// boolean object - false represents suffix not started, true suffix in progress, null suffix ended
				Boolean suffixstarted = false;
				for (int i = 0; i < args.length; i++) {
					if (target == null) {
						if (args[i].startsWith("\"")) {
							suffixstarted = true;
						}
						if (suffixstarted == true) {
							suffix += " " + args[i];
							if (args[i].endsWith("\"")) {
								suffixstarted = null;
							}
						}
						if ((suffixstarted == null) && (i < (args.length - 1))) {
							target = args[i + 1];
						}
					}
				}
				if (suffixstarted == null) {
					// trim off the start and end speech marks
					suffix = suffix.substring(2, suffix.length() - 1);
				}
				// if the suffix never started, assume single word suffix and pick a target if it was specified.
				if (!suffixstarted) {
					suffix = args[0];
					if (args.length > 1) {
						target = args[1];
					}
				}
				if (target == null) {
					if (!(sender instanceof Player)) {
						// if only one arg specified take target to be self, but this only works if you are a player
						sender.sendMessage("You cannot do this since you are not a player.");
						return false;
					} else {
						// however if it was a player that issued the command, execute it.
						// permissions check
						if (sender.hasPermission("ordosname.name.suffix.self")) {
							try {
								Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
								ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + sender.getName() + "';");
								if (!(tryRS == null) && (tryRS.first())) {
									// if there's a result, update the table instead of inserting.
									statement.executeUpdate("UPDATE ordosname SET suffix = '" + suffix + "', lastseen = '" + timestamp
											+ "' WHERE user= '" + sender.getName() + "';");
								} else {
									// If no result was returned then the user has not been added before.
									// Use INSERT instead of update to create the record.
									statement.executeUpdate("INSERT INTO ordosname (user, suffix, last, lastseen) VALUES ('" + sender.getName()
											+ "', '" + suffix + "', '" + sender.getName() + "', '" + timestamp + "');");
								}
								if (statement != null) {
									statement.close();
								}
							} catch (SQLException e) {
								e.printStackTrace();
							}
							reloadPlayerName((Player) sender);
							return true;
						} else {
							// "You don't have permission!"
						}
					}
				} else {
					// permission check
					if (sender.hasPermission("ordosname.name.suffix.others")) {
						try {
							Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + target + "';");
							if (!(tryRS == null) && (tryRS.first())) {
								// if there's a result, update the table instead of inserting.
								statement.executeUpdate("UPDATE ordosname SET suffix = '" + suffix + "', lastseen = '" + timestamp
										+ "' WHERE user= '" + target + "';");
							} else {
								// If no result was returned then the user has not been added before.
								// Use INSERT instead of update to create the record.
								statement.executeUpdate("INSERT INTO ordosname (user, suffix, last, lastseen) VALUES ('" + target + "', '" + suffix
										+ "', '" + target + "', '" + timestamp + "');");
							}
							if (statement != null) {
								statement.close();
							}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						reloadPlayerName((Player) sender);
						return true;
					} else {
						// "You don't have permission!"
					}
				}
			}
		}

		return false;
	}

	private void dbcleanup() {
		// sql query for datediff (much easier than doing so in-code)
		try {
			Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet RS = statement.executeQuery("SELECT * FROM ordosname WHERE (DATEDIFF(minute, lastseen, GETDATE()) > " + dbcleanuptime + ");");
			// if there were results, delete them
			if (!(RS == null) && (RS.first())) {
				logger.info("Found " + getResultSetNumRows(RS) + " records to delete.");
				statement.executeUpdate("DELETE FROM ordosname WHERE (DATEDIFF(minute, lastseen, GETDATE()) > " + dbcleanuptime + ");");
			} else {
				logger.info("Found 0 records to delete.");
			}
			logger.info("Database cleanup complete.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void reloadPlayerName(CommandSender sender, String playername) {
		Statement statement;
		try {
			statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet RS = statement.executeQuery("SELECT * FROM ordosname WHERE user = '" + playername + "';");
			if (!(RS == null) && (RS.first())) {
				if (!(RS.getBoolean("enabled"))) {
					sender.sendMessage(ChatColor.RED + "Data was found, but ENABLED was flagged FALSE");
				} else {
					// if there's a result, set the player's name appropriately.
					Player player = server.getPlayer(playername);
					if (player == null) {
						sender.sendMessage(ChatColor.RED + "Player is offline.");
					} else {
						// fetch name objects and append appropriate spacing
						String title = RS.getString("title");
						String last = RS.getString("last");
						String suffix = RS.getString("suffix");
						String first;
						// does the player's title override their first name, and do they have a title?
						if ((title != null) && (RS.getBoolean("titleoverridesfirst"))) {
							// if so, we won't be needing firstname.
							first = null;
						} else {
							// if not, we'll need to fetch their first name
							first = RS.getString("first");
						}
						// string of final name to be set
						String name = "";
						if (title != null) {
							name += title + " ";
						}
						if (first != null) {
							name += first + " ";
						}
						if (last != null) {
							name += last + " ";
						}
						if (suffix != null) {
							name += suffix;
						}
						if (name.endsWith(" ")) {
							name = name.substring(0, name.length() - 1);
						}
						if (name.length() < 1) {
							sender.sendMessage(ChatColor.RED + "Data was found, but all fields were NULL");
						} else {
							sender.sendMessage(ChatColor.RED + "Data was found, name reloaded.");
							recordDisplayName(playername, name);
							player.setDisplayName(name);
						}
					}
				}
			} else {
				// If no result was returned then the user has no record. Return an error.
				sender.sendMessage(ChatColor.RED + "No data found for player " + playername);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void reloadPlayerName(Player player) {
		try {
			Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet RS = statement.executeQuery("SELECT * FROM ordosname WHERE user = '" + player.getName() + "';");
			if (!(RS == null) && (RS.first())) {
				if (!(RS.getBoolean("enabled"))) {
					if (verbose) {
						logger.info(ChatColor.RED + "Data was found, but ENABLED was flagged FALSE");
					}
				} else {
					// if there's a result, set the player's name appropriately.
					// fetch name objects and append appropriate spacing
					String title = RS.getString("title");
					String last = RS.getString("last");
					String suffix = RS.getString("suffix");
					String first;
					// does the player's title override their first name, and do they have a title?
					if ((title != null) && (RS.getBoolean("titleoverridesfirst"))) {
						// if so, we won't be needing firstname.
						first = null;
					} else {
						// if not, we'll need to fetch their first name
						first = RS.getString("first");
					}
					// string of final name to be set
					String name = "";
					if (title != null) {
						name += title + " ";
					}
					if (first != null) {
						name += first + " ";
					}
					if (last != null) {
						name += last + " ";
					}
					if (suffix != null) {
						name += suffix;
					}
					if (name.endsWith(" ")) {
						name = name.substring(0, name.length() - 1);
					}
					if (name.length() < 1) {
						if (verbose) {
							logger.info(ChatColor.RED + "Data was found, but all fields were NULL");
						}
					} else {
						if (verbose) {
							logger.info(ChatColor.RED + "Data was found, name reloaded.");
						}
						player.setDisplayName(name);
						recordDisplayName(player.getName(), name);
					}
				}
			} else {
				if (verbose) {
					// If no result was returned then the user has no record. Return an error.
					logger.info(ChatColor.RED + "No data found for player " + player.getName());
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void recordDisplayName(String user, String name) {
		try {
			Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			statement.executeUpdate("UPDATE ordosname SET displayname = '" + name + "' WHERE user= '" + user + "';");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@EventHandler
	// EventPriority.NORMAL by default
	public void onPlayerLogin(PlayerLoginEvent event) {
		// get timestamp for DB inserts
		Object timestamp = new java.sql.Timestamp((new Date()).getTime());

		Player player = event.getPlayer();
		reloadPlayerName(player);

		try {
			Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet tryRS = statement.executeQuery("SELECT user FROM ordosname WHERE user = '" + player.getName() + "';");
			if (!(tryRS == null) && (tryRS.first())) {
				// if the user is already in the database, just update their record with the new login time.
				statement.executeUpdate("UPDATE ordosname SET lastseen = '" + timestamp + "' WHERE user= '" + player.getName() + "';");
			} else {
				// if the user is not already in the database, insert a new record with their username (so that suffixes and titles don't look stupid)
				statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
				statement.executeQuery("INSERT INTO ordosname (user, last, lastseen) VALUES ('" + player.getName() + "', '" + player.getName()
						+ "', '" + timestamp + "');");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}