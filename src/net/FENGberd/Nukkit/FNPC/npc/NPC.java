package net.FENGberd.Nukkit.FNPC.npc;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import net.FENGberd.Nukkit.FNPC.Main;
import net.FENGberd.Nukkit.FNPC.utils.RegisteredNPC;
import net.FENGberd.Nukkit.FNPC.utils.Utils;

import java.io.File;
import java.util.*;

@SuppressWarnings("unused")
public class NPC extends cn.nukkit.level.Location
{
	public static HashMap<String,NPC> pool=new HashMap<>();
	public static Config config;
	public static DataPacket packet_hash;
	public static HashMap<String,HashMap<String,Object>> unknownTypeData=new HashMap<>();
	/**
	 * 静态分割线********************************
	 */

	public String nametag="";
	public float scale = 1f;
	public Item handItem=null;
	public Item[] equipment = new Item[4];
	public String skinpath="";
	public boolean isSlim=false;
	public String level="";
	public UUID uuid=null;
	public String extra="";
	public Skin skin=new Skin(new byte[Skin.SINGLE_SKIN_SIZE],"Standard_Custom");
	protected long eid=0;
	protected String nid="";
	public NPC(String nid,String nametag,double x,double y,double z,Item handItem,Item[] equipment)
	{
		this.nid=nid;
		this.nametag=nametag;
		this.x=x;
		this.y=y;
		this.z=z;
		this.handItem=handItem==null?Item.get(0):handItem;
		this.uuid=new UUID(new Random().nextLong(),new Random().nextLong());
		this.eid=Entity.entityCount++;
		NPC existsCheck=NPC.pool.getOrDefault(nid,null);
		if(existsCheck!=null)
		{
			Main.getInstance().getLogger().warning(TextFormat.YELLOW+"警告:尝试创建ID重复NPC:"+TextFormat.AQUA+nid+TextFormat.YELLOW+" ,请检查是否出现逻辑错误");
			existsCheck.close();
		}
		NPC.pool.put(nid,this);
	}
	public NPC(String nid,String nametag,double x,double y,double z)
	{
		this(nid,nametag,x,y,z,null,null);
	}
	public NPC(String nid,String nametag,double x,double y,double z,Item handItem)
	{
		this(nid,nametag,x,y,z,handItem,null);
	}
	public NPC(String nid,String nametag,double x,double y,double z,Item[] equipment)
	{
		this(nid,nametag,x,y,z,null,equipment);
	}
	public NPC(String nid)
	{
		this(nid,"",0,0,0);
	}

	public static void reloadUnknownNPC()
	{
		ArrayList<String> remove=new ArrayList<>();
		for(String key:NPC.unknownTypeData.keySet())
		{
			HashMap<String,Object> val=Utils.cast(config.get(key));
			RegisteredNPC npc=Main.getRegisteredNpcClass((String)val.get("type"));
			if(npc!=null)
			{
				try
				{
					npc.npcClass.getConstructor(String.class).newInstance(key).reload();
				}
				catch(Exception e)
				{
					Main.getInstance().getLogger().warning(TextFormat.YELLOW+"加载ID为 "+TextFormat.AQUA+key+TextFormat.YELLOW+" 的NPC时出现了未知错误");
				}
				remove.add(key);
			}
		}
		remove.forEach(NPC.unknownTypeData::remove);
	}

	public static void init()
	{
		if(Main.getInstance().getDataFolder().mkdirs() || new File(Main.getInstance().getDataFolder().toString()+"/skins/").mkdirs() || new File(Main.getInstance().getDataFolder().toString()+"/skins/cache").mkdirs())
		{
			Main.getInstance().getLogger().debug("配置文件已创建");
		}
		NPC.pool=new HashMap<>();
		NPC.config=new Config(Main.getInstance().getDataFolder().toString()+"/NPC.yml",Config.YAML);
		for(String key : config.getAll().keySet())
		{
			HashMap<String,Object> val=Utils.cast(config.get(key));
			RegisteredNPC npc=Main.getRegisteredNpcClass((String)val.get("type"));
			if(npc!=null)
			{
				try
				{
					npc.npcClass.getConstructor(String.class).newInstance(key).reload();
				}
				catch(Exception e)
				{
					e.printStackTrace();
					Main.getInstance().getLogger().warning(TextFormat.YELLOW+"加载ID为 "+TextFormat.AQUA+key+TextFormat.YELLOW+" 的NPC时出现了未知错误");
				}
			}
			else
			{
				unknownTypeData.put(key,val);
			}
		}
	}

	public static void spawnAllTo(Player player)
	{
		spawnAllTo(player,null);
	}

	public static void spawnAllTo(Player player,Level level)
	{
		for(NPC npc:NPC.pool.values())
		{
			npc.spawnTo(player,level);
		}
	}

	public static void packetReceive(Player player,DataPacket packet)
	{
		if(packet.pid()==ProtocolInfo.INTERACT_PACKET && !packet.equals(NPC.packet_hash) && ((InteractPacket)packet).action==InteractPacket.ACTION_LEFT_CLICK)
		{
			NPC.packet_hash=packet;
			NPC.pool.values().stream().filter(npc->((InteractPacket)packet).target==npc.getEID()).forEach(npc->npc.onTouch(player));
		}
	}

	public static void tick()
	{
		NPC.pool.values().forEach(net.FENGberd.Nukkit.FNPC.npc.NPC::onTick);
	}

	public static void playerMove(Player player)
	{
		NPC.pool.values().stream().filter(npc->npc.distance(player)<=10).forEach(npc->npc.look(player));
	}

	public void look(Player player)
	{
		double x=this.x-player.x, y=this.y-player.y, z=this.z-player.z;
		double yaw=Math.asin(x/Math.sqrt(x*x+z*z))/3.14*180, pitch=Math.round(Math.asin(y/Math.sqrt(x*x+z*z+y*y))/3.14*180);
		if(z>0)
		{
			yaw=-yaw+180;
		}
		MovePlayerPacket pk=new MovePlayerPacket();
		pk.eid=this.getEID();
		pk.x=Float.parseFloat(String.valueOf(this.x));
		pk.y=Float.parseFloat(String.valueOf(this.y+1.62));
		pk.z=Float.parseFloat(String.valueOf(this.z));
		pk.headYaw=pk.yaw=Float.parseFloat(String.valueOf(yaw));
		pk.pitch=Float.parseFloat(String.valueOf(pitch));
		pk.mode=0;
		player.dataPacket(pk);
	}

	public HashMap<String,Object> reload()
	{
		HashMap<String,Object> cfg=Utils.cast(NPC.config.get(this.nid,null));
		if(cfg!=null)
		{
			this.x=Utils.cast(cfg.getOrDefault("x",0));
			this.y=Utils.cast(cfg.getOrDefault("y",0));
			this.z=Utils.cast(cfg.getOrDefault("z",0));
			this.level=String.valueOf(cfg.getOrDefault("level",""));
			this.yaw=Utils.cast(cfg.getOrDefault("yaw",0));
			this.pitch=Utils.cast(cfg.getOrDefault("pitch",0));
			this.nametag=Utils.cast(cfg.getOrDefault("nametag",""));
			this.scale =Utils.cast(Float.valueOf(cfg.getOrDefault("scale",0).toString()));
			this.skin.setModel(String.valueOf(cfg.getOrDefault("skinName",this.skin.getModel())));
			this.extra=String.valueOf(cfg.getOrDefault("extra",""));
			HashMap<String,Object> itemCfg=Utils.cast(cfg.getOrDefault("handItem",new HashMap<String,Object>()));
			Item handitem = Item.get(Utils.cast(itemCfg.getOrDefault("id",0)),Utils.cast(itemCfg.getOrDefault("data",0)));
			if((boolean)itemCfg.get("isEnchant")){
				if(itemCfg.containsKey("Enchants")){
					Map<Integer,Integer> enchants = (Map<Integer, Integer>) itemCfg.get("Enchants");
					for(Integer id:enchants.keySet()){
						Enchantment enchantment = Enchantment.getEnchantment(id);
						enchantment.setLevel(enchants.get(id));
						handitem.addEnchantment(enchantment);
					}
				}
			}
			this.handItem=handitem;
			HashMap<String,Object> equipmentCfg=Utils.cast(cfg.getOrDefault("equipment",new HashMap<String,Object>()));
			String[] equipments = new String[]{"helmet","chestplate","leggings","boots"};
			Item[] equipment = new Item[4];
			for(int i=0;i<4;i++){
				HashMap<String,Object> content = (HashMap)equipmentCfg.get(equipments[i]);
				Item item = Item.get((int)content.get("id"));
				if((boolean)content.get("isEnchant")){
					if(!content.containsKey("Enchants")){
						continue;
					}
					Map<Integer,Integer> enchants = (Map<Integer, Integer>) content.get("Enchants");
					for(Integer id:enchants.keySet()){
						Enchantment enchantment = Enchantment.getEnchantment(id);
						enchantment.setLevel(enchants.get(id));
						item.addEnchantment(enchantment);
					}
				}
				equipment[i] = item;
			}
			this.equipment= equipment;
			byte[] skinData=Utils.getPngSkin(new File(Main.getInstance().getDataFolder().toString()+"/skins/"+String.valueOf(cfg.getOrDefault("skin",""))));
			if(skinData.length>0)
			{
				this.skinpath=String.valueOf(cfg.getOrDefault("skin",""));
				this.skin.setData(skinData);
			}
			return cfg;
		}
		return null;
	}

	public void setName(String name)
	{
		this.nametag=name.replace("\\n","\n");
		this.save();
		this.spawnToAll();
	}

	public boolean setPNGSkin(String path)
	{
		if(path==null)
		{
			this.skin=new Skin(new byte[Skin.SINGLE_SKIN_SIZE],"Standard_Custom");
			return true;
		}
		else
		{
			File f=new File(Main.getInstance().getDataFolder().toString()+"/skins/"+path);
			if(!f.isFile())
			{
				return false;
			}
			byte[] skin=Utils.getPngSkin(f,false);
			if(skin.length<=0)
			{
				return false;
			}
			this.skin.setData(skin);
			this.skinpath=path;
			this.save();
			this.spawnToAll();
		}
		return true;
	}

	public void setHandItem(Item item)
	{
		this.handItem=item;
		this.save();
		this.spawnToAll();
	}

	public void setEquipment(Item[] armor){
		this.equipment = armor;
		this.save();
		this.spawnToAll();
	}

	public void close()
	{
		this.close(true);
	}

	public void close(boolean removeData)
	{
		this.despawnFromAll();
		if(removeData)
		{
			NPC.config.remove(this.getId());
			NPC.config.save();
		}
		NPC.pool.remove(this.getId());
	}

	public long getEID()
	{
		return this.eid;
	}

	public float getScale(){
		return this.scale;
	}

	public void setScale(float scale){
		this.scale = scale;
		this.save();
		this.spawnToAll();
	}

	public Skin getSkin()
	{
		return this.skin;
	}

	public void setSkin(Skin skin){
		this.skin = skin;
	}

	public String getSkinPath()
	{
		return this.skinpath;
	}

	public String getLevelName()
	{
		return this.level;
	}

	public String getId()
	{
		return this.nid;
	}

	public void onTick()
	{

	}

	public void onTouch(Player player)
	{

	}

	public void teleport(Vector3 pos)
	{
		this.x=pos.x;
		this.y=pos.y;
		this.z=pos.z;
		if(pos instanceof Position)
		{
			this.level=((Position)pos).getLevel().getFolderName();
			this.spawnToAll();
		}
		else
		{
			this.sendPosition();
		}
	}

	public void save()
	{
		HashMap<String,Object> extra=new HashMap<>();
		extra.put("type","normal");
		this.save(extra);
	}

	public void save(HashMap<String,Object> extra)
	{
		extra.put("x",this.x);
		extra.put("y",this.y);
		extra.put("z",this.z);
		extra.put("level",this.level);
		extra.put("yaw",this.yaw);
		extra.put("pitch",this.pitch);
		extra.put("skin",this.skinpath);
		extra.put("nametag",this.nametag);
		extra.put("scale",this.scale);
		extra.put("skinName",this.skin.getModel());
		extra.put("extra",this.extra);
		HashMap<String,Object> handItem=new HashMap<>();
		handItem.put("id",this.handItem.getId());
		handItem.put("data",this.handItem.getDamage());
		handItem.put("isEnchant",this.handItem.hasEnchantments());
		HashMap<Integer,Integer> Itemenchants = new HashMap<>();
		if(this.handItem.hasEnchantments()){
			for (Enchantment enchantment:this.handItem.getEnchantments()){
				Itemenchants.put(enchantment.getId(),enchantment.getLevel());
			}
		}
		handItem.put("Enchants",Itemenchants);
		extra.put("handItem",handItem);
		HashMap<String,Object> equipment=new HashMap<>();
		String[] equipments = new String[]{"helmet","chestplate","leggings","boots"};
		for(int i =0;i<4;i++){
			HashMap<String,Object> content=new HashMap<>();
			content.put("isEnchant",this.equipment[i].hasEnchantments());
			HashMap<Integer,Integer> enchants = new HashMap<>();
			for (Enchantment enchantment:this.equipment[i].getEnchantments()){
				enchants.put(enchantment.getId(),enchantment.getLevel());
			}
			content.put("Enchants",enchants);
			content.put("id",this.equipment[i].getId());
			equipment.put(equipments[i],content);
		}
		extra.put("equipment",equipment);
		NPC.config.set(this.getId(),extra);
		NPC.config.save();
	}

	public void despawnFromAll()
	{
		Level level=Main.getInstance().getServer().getLevelByName(this.level);
		ArrayList<Player> players=new ArrayList<>();
		if(level!=null)
		{
			players.addAll(level.getPlayers().values());
		}
		else
		{
			players.addAll(Main.getInstance().getServer().getOnlinePlayers().values());
		}
		for(Object p:players.toArray())
		{
			this.despawnFrom(Utils.cast(p));
		}
	}

	public void despawnFrom(Player player)
	{
		RemoveEntityPacket pk=new RemoveEntityPacket();
		pk.eid=this.getEID();
		player.dataPacket(pk);
		Server.getInstance().removePlayerListData(this.uuid,new Player[]{player});
	}

	public void spawnToAll()
	{
		Level level=Main.getInstance().getServer().getLevelByName(this.level);
		ArrayList<Player> players=new ArrayList<>();
		if(level!=null)
		{
			players.addAll(level.getPlayers().values());
		}
		else
		{
			players.addAll(Main.getInstance().getServer().getOnlinePlayers().values());
		}
		for(Object p:players.toArray())
		{
			this.spawnTo(Utils.cast(p));
		}
	}
	
	public boolean spawnTo(Player player)
	{
		return this.spawnTo(player,null);
	}

	public boolean spawnTo(Player player,Level level)
	{
		if(level==null)
		{
			level=player.getLevel();
		} 
		if(!this.getLevelName().equals("") && !level.getFolderName().toLowerCase().equals(this.getLevelName().toLowerCase()))
		{
			this.despawnFrom(player);
			return false;
		}
		Server.getInstance().updatePlayerListData(this.uuid,this.getEID(),this.nametag,this.skin,new Player[]{player});
		AddPlayerPacket pk=new AddPlayerPacket();
		pk.username=this.nametag;
		pk.entityUniqueId=this.getEID();
		pk.entityRuntimeId=this.getEID();
		pk.uuid=this.uuid;
		pk.x=Float.parseFloat(String.valueOf(this.x));
		pk.y=Float.parseFloat(String.valueOf(this.y));
		pk.z=Float.parseFloat(String.valueOf(this.z));
		pk.yaw=Float.parseFloat(String.valueOf(this.yaw));
		pk.pitch=Float.parseFloat(String.valueOf(this.pitch));
		pk.speedX=0;
		pk.speedY=0;
		pk.speedZ=0;
		pk.item=this.handItem;
		long flags=0x00;
		flags^=1<<Entity.DATA_FLAG_CAN_SHOW_NAMETAG;
		flags^=1<<Entity.DATA_FLAG_ALWAYS_SHOW_NAMETAG;
		pk.metadata=new EntityMetadata()
			.putLong(Entity.DATA_FLAGS,flags)
			.putShort(Entity.DATA_AIR,400)
			.putShort(Entity.DATA_MAX_AIR,400)
			.putString(Entity.DATA_NAMETAG,this.nametag)
			.putLong(Entity.DATA_LEAD_HOLDER_EID,-1)
			.putFloat(Entity.DATA_SCALE,scale);
		player.dataPacket(pk);
		Server.getInstance().removePlayerListData(this.uuid,new Player[]{player});
		Item[] armor= this.equipment;
        sendEquipments(armor,player);
        if(this.skinpath.equals("")){
        	setSkin(player.getSkin());
		}
		return true;
	}

	public void sendEquipments(Item[] armor,Player player){
		MobArmorEquipmentPacket armorPk=new MobArmorEquipmentPacket();
		armorPk.eid=this.getEID();
		armorPk.slots=armor;
		armorPk.encode();
		armorPk.isEncoded=true;
		player.dataPacket(armorPk);
	}

	public void sendEquipmentToAll(Item[] armor){
		for(Object p:Main.getInstance().getServer().getOnlinePlayers().values())
		{
			sendEquipments(armor,((Player)p));
		}
	}

	public void sendPosition()
	{
		MovePlayerPacket pk=new MovePlayerPacket();
		pk.eid=this.getEID();
		pk.x=Utils.cast(this.x);
		pk.y=Utils.cast(this.y+1.62);
		pk.z=Utils.cast(this.z);
		pk.headYaw=pk.yaw=Utils.cast(this.yaw);
		pk.pitch=Utils.cast(this.pitch);
		pk.mode=0;
		for(Object p:Main.getInstance().getServer().getOnlinePlayers().values())
		{
			((Player)p).dataPacket(pk);
		}
	}
}