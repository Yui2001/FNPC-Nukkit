package net.FENGberd.Nukkit.FNPC;

import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.args.CommandArg;
import cn.nukkit.command.data.args.CommandArgBlockVector;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.network.protocol.AvailableCommandsPacket;
import cn.nukkit.network.protocol.CommandStepPacket;
import co.aikar.timings.Timings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.FENGberd.Nukkit.FNPC.commands.NpcCommand;
import net.FENGberd.Nukkit.FNPC.npc.CommandNPC;
import net.FENGberd.Nukkit.FNPC.npc.NPC;
import net.FENGberd.Nukkit.FNPC.npc.ReplyNPC;
import net.FENGberd.Nukkit.FNPC.npc.TeleportNPC;
import net.FENGberd.Nukkit.FNPC.tasks.QuickSystemTask;
import net.FENGberd.Nukkit.FNPC.utils.RegisteredNPC;
import net.FENGberd.Nukkit.FNPC.utils.Utils;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Main extends cn.nukkit.plugin.PluginBase implements cn.nukkit.event.Listener
{
	private static Main obj=null;
	private static HashMap<String,RegisteredNPC> registeredNPC=new HashMap<>();
	/**
	 * 静态分割线********************************
	 */

	 NpcCommand npcCommand=null;

	public static Main getInstance()
	{
		return Main.obj;
	}

	public static HashMap<String,RegisteredNPC> getRegisteredNpcs()
	{
		return registeredNPC;
	}

	public static RegisteredNPC getRegisteredNpcClass(String name)
	{
		RegisteredNPC npc=Main.registeredNPC.getOrDefault(name.toLowerCase(),null);
		if(npc==null)
		{
			return null;
		}
		return npc;
	}

	public static void unregisterNpc(String name)
	{
		Main.registeredNPC.remove(name.toLowerCase());
	}

	public static boolean registerNpc(String name,String description,Class npcClass)
	{
		return Main.registerNpc(name,description,npcClass,false);
	}

	public static boolean registerNpc(String name,String description,Class npcClass,boolean force)
	{
		name=name.toLowerCase();
		if(NPC.class.isAssignableFrom(npcClass) && ! npcClass.isInterface() && (Main.registeredNPC.getOrDefault(name,null)==null || force))
		{
			Main.registeredNPC.put(name,new RegisteredNPC(Utils.cast(npcClass),name,description));
			NPC.reloadUnknownNPC();
			return true;
		}
		return false;
	}
	
	@Override
	public void onEnable()
	{
		if(Main.obj==null)
		{
			Main.obj=this;
			Main.registerNpc("normal","普通NPC(无实际功能)",NPC.class,true);
			Main.registerNpc("reply","回复型NPC(使用/fnpc chat)",ReplyNPC.class,true);
			Main.registerNpc("command","指令型NPC(使用/fnpc command)",CommandNPC.class,true);
			Main.registerNpc("teleport","传送型NPC(使用/fnpc teleport或/fnpc transfer)",TeleportNPC.class,true);
		}
		NPC.init();
		Utils.loadLang(this.getServer().getLanguage());
		QuickSystemTask quickSystemTask=new QuickSystemTask(this);
		npcCommand=new NpcCommand();
		this.getServer().getCommandMap().register("fnpc",npcCommand);
		this.getServer().getPluginManager().registerEvents(this,this);
		this.getServer().getScheduler().scheduleRepeatingTask(quickSystemTask,1);
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onPlayerMove(cn.nukkit.event.player.PlayerMoveEvent event)
	{
		NPC.playerMove(event.getPlayer());
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onDataPacketReceive(DataPacketReceiveEvent event)
	{
		if(event.getPacket() instanceof CommandStepPacket)
		{
			CommandStepPacket pk=Utils.cast(event.getPacket());
			if(pk.command.startsWith("fnpc "))
			{
				String commandText=pk.command;
				//if(pk.args!=null) 这个判断使/fnpc help 和/fnpc type 无法使用
				{
					CommandParameter[] pars=npcCommand.getCommandParameters(pk.overload);
					if(pars!=null)
					{
						for(CommandParameter par:pars)
						{
							JsonElement arg=pk.args.get(par.name);
							if(arg!=null)
							{
								switch(par.type)
								{
								case CommandParameter.ARG_TYPE_TARGET:
									CommandArg rules=new Gson().fromJson(arg,CommandArg.class);
									commandText+=" "+rules.getRules()[0].getValue();
									break;
								case CommandParameter.ARG_TYPE_BLOCK_POS:
									CommandArgBlockVector bv=new Gson().fromJson(arg,CommandArgBlockVector.class);
									commandText+=" "+bv.getX()+" "+bv.getY()+" " + bv.getZ();
									break;
								case CommandParameter.ARG_TYPE_STRING:
								case CommandParameter.ARG_TYPE_STRING_ENUM:
								case CommandParameter.ARG_TYPE_RAW_TEXT:
									String string=new Gson().fromJson(arg, String.class);
									commandText+=" "+string;
									break;
								default:
									commandText+=" "+arg.toString();
									break;
								}
							}
						}
					}
					//this.getLogger().warning(commandText);
					PlayerCommandPreprocessEvent playerCommandPreprocessEvent=new PlayerCommandPreprocessEvent(event.getPlayer(),"/"+commandText);
					this.getServer().getPluginManager().callEvent(playerCommandPreprocessEvent);
					if(!playerCommandPreprocessEvent.isCancelled())
					{
						Timings.playerCommandTimer.startTiming();
						this.getServer().dispatchCommand(playerCommandPreprocessEvent.getPlayer(),playerCommandPreprocessEvent.getMessage().substring(1));
						Timings.playerCommandTimer.stopTiming();
					}
				}
				event.setCancelled(true);
			}
		}
		else
		{
			NPC.packetReceive(event.getPlayer(),event.getPacket());
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onDataPacketSend(DataPacketSendEvent event)
	{
		if(event.getPacket() instanceof AvailableCommandsPacket)
		{
			AvailableCommandsPacket pk=Utils.cast(event.getPacket());
			Map<String,CommandDataVersions> data=new Gson().fromJson(pk.commands,Map.class);
			npcCommand.processCustomCommandData(data);
			pk.commands=new Gson().toJson(data);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onPlayerJoin(cn.nukkit.event.player.PlayerJoinEvent event)
	{
		NPC.spawnAllTo(event.getPlayer());
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onEntityLevelChange(cn.nukkit.event.entity.EntityLevelChangeEvent event)
	{
		if(event.getEntity() instanceof cn.nukkit.Player)
		{
			NPC.spawnAllTo(Utils.cast(event.getEntity()),event.getTarget());
		}
	}
}
