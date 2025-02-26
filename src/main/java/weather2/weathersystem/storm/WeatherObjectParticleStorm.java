package weather2.weathersystem.storm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import weather2.WeatherBlocks;
import weather2.client.SceneEnhancer;
import weather2.config.ConfigSand;
import weather2.config.ConfigSnow;
import weather2.datatypes.PrecipitationType;
import weather2.util.CachedNBTTagCompound;
import weather2.util.WeatherUtilBlock;
import weather2.weathersystem.WeatherManager;
import weather2.weathersystem.wind.WindManager;

import java.util.Random;

public class WeatherObjectParticleStorm extends WeatherObject {

	public int age = 0;
	public int maxAge = 20*20;

	public Random rand = new Random();

	public StormType type;

	public enum StormType {
		SANDSTORM("SANDSTORM"),
		SNOWSTORM("SNOWSTORM");

		private final String key;

		public static final StormType[] VALUES = values();

		StormType(String key) {
			this.key = key;
		}

		public String getKey() {
			return key;
		}
	}

	public WeatherObjectParticleStorm(WeatherManager parManager) {
		super(parManager);
		
		this.weatherObjectType = EnumWeatherObjectType.SAND;
	}
	
	public void initStormSpawn(Vec3 pos) {
		this.pos = pos;
		this.maxAge = 20*60*5;
	}

	public static boolean canSpawnHere(Level world, BlockPos pos, StormType type, boolean forSpawn) {
		Biome biomeIn = world.m_204166_(pos).m_203334_();
		if (type == StormType.SANDSTORM) {
			return isDesert(biomeIn, forSpawn);
		} else if (type == StormType.SNOWSTORM) {
			return isColdForStorm(world, biomeIn, forSpawn, pos);
		}
		return false;
	}

	public static boolean isColdForStorm(Level world, Biome biome, boolean forSpawn, BlockPos pos) {
		//return biome.getPrecipitation() == Biome.Precipitation.SNOW;
		//adjusted to this way to make it work with serene seasons
		boolean canPrecip = biome.getPrecipitation() == Biome.Precipitation.RAIN || biome.getPrecipitation() == Biome.Precipitation.SNOW;
		return canPrecip && SceneEnhancer.shouldSnowHere(world, biome, pos);
	}

	public static boolean isDesert(Biome biome, boolean forSpawn) {
		//TODO: make sure new comparison works
		return biome.equals(Biomes.DESERT) || (!forSpawn && biome.equals(Biomes.RIVER)) || biome.getRegistryName().toString().toLowerCase().contains("desert");
	}

	@Override
	public int getSize() {
		return 250;
	}
	
	@Override
	public void tick() {
		super.tick();

		if (!manager.getWorld().isClientSide()) {
			this.age++;
			//CULog.dbg("this.age: " + this.age);
			if (this.age > this.maxAge) {
				this.remove();
			}

			if (getIntensity() > 0.2D) {
				tickBlockSandBuildup();
			}
		}

		posGround = pos;
	}

	/**
	 * 0-1F for first half of age, 1-0F for second half of age
	 * @return
	 */
	public float getIntensity() {
		float age = this.age;
		float maxAge = this.maxAge;
		if (age / maxAge <= 0.5F) {
			return age / (maxAge/2);
		} else {
			return 1F - (age / (maxAge/2) - 1F);
		}
	}

	@OnlyIn(Dist.CLIENT)
	public void tickClient() {

	}

	public Block getBlockForBuildup() {
		if (this.type == StormType.SANDSTORM) {
			return WeatherBlocks.BLOCK_SAND_LAYER.get();
		} else if (this.type == StormType.SNOWSTORM) {
			return Blocks.SNOW;
		}
		return null;
	}

	public void tickBlockSandBuildup() {

		Level world = manager.getWorld();
		WindManager windMan = manager.getWindManager();

		float angle = windMan.getWindAngleForClouds();

		//keep it set to do a lot of work only occasionally, prevents chunk render tick spam for client which kills fps
		int delay = ConfigSand.Sandstorm_Sand_Buildup_TickRate;
		int loop = (int)((float)ConfigSand.Sandstorm_Sand_Buildup_LoopAmountBase * getIntensity());
		boolean buildupOutsideArea = ConfigSand.Sandstorm_Sand_Buildup_AllowOutsideDesert;
		int maxBlockStackingAllowed = ConfigSand.Sandstorm_Sand_Block_Max_Height;

		if (getType() == StormType.SNOWSTORM) {
			delay = ConfigSnow.Snowstorm_Snow_Buildup_TickRate;
			loop = (int)((float)ConfigSnow.Snowstorm_Snow_Buildup_LoopAmountBase * getIntensity());
			buildupOutsideArea = ConfigSnow.Snowstorm_Snow_Buildup_AllowOutsideColdBiomes;
			maxBlockStackingAllowed = ConfigSnow.Snowstorm_Snow_Block_Max_Height;
		}

		//sand block buildup
		if (!world.isClientSide) {
			if (getBlockForBuildup() != null) {
				if (world.getGameTime() % delay == 0) {

					for (int i = 0; i < loop; i++) {

						//rate of placement based on storm intensity
						if (rand.nextDouble() >= getIntensity()) continue;

						Vec3 vecPos = getRandomPosInStorm();

						BlockPos blockPos = WeatherUtilBlock.getPrecipitationHeightSafe(world, new BlockPos(vecPos.x, 0, vecPos.z));

						//avoid unloaded areas
						if (!world.hasChunkAt(blockPos)) continue;

						if (buildupOutsideArea ||
								canSpawnHere(world, blockPos, getType(), false)) {
							WeatherUtilBlock.fillAgainstWallSmoothly(world, new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()), angle, 15, 2, getBlockForBuildup(), maxBlockStackingAllowed);
						}
					}
				}
			}
		}
	}

	public Vec3 getRandomPosInStorm() {
		Random rand = new Random();
		int x = (int) Math.floor(posGround.x + rand.nextInt(getSize()) - rand.nextInt(getSize()));
		int z = (int) Math.floor(posGround.z + rand.nextInt(getSize()) - rand.nextInt(getSize()));
		int y = WeatherUtilBlock.getPrecipitationHeightSafe(manager.getWorld(), new BlockPos(x, 128, z)).getY();
		Vec3 vec = new Vec3(x, y, z);
		return vec;
	}
	
	@Override
	public int getUpdateRateForNetwork() {
		return 1;
	}
	
	@Override
	public void nbtSyncForClient() {
		super.nbtSyncForClient();
		CachedNBTTagCompound data = this.getNbtCache();
		data.putInt("age", age);
		data.putInt("maxAge", maxAge);
		data.putString("type", type.key);
		data.putString("test", "WHAT");
	}
	
	@Override
	public void nbtSyncFromServer() {
		super.nbtSyncFromServer();
		CachedNBTTagCompound parNBT = this.getNbtCache();
		this.age = parNBT.getInt("age");
		this.maxAge = parNBT.getInt("maxAge");
		if (parNBT.contains("type")) {
			this.type = StormType.valueOf(parNBT.getString("type").toUpperCase());
		}
	}

	@Override
	public void read()
	{
		super.read();
		nbtSyncFromServer();
		CachedNBTTagCompound var1 = this.getNbtCache();
		motion = new Vec3(var1.getDouble("vecX"), var1.getDouble("vecY"), var1.getDouble("vecZ"));
	}

	@Override
	public void write()
	{
		super.write();
		nbtSyncForClient();

		CachedNBTTagCompound nbt = this.getNbtCache();

		nbt.putDouble("vecX", motion.x);
		nbt.putDouble("vecY", motion.y);
		nbt.putDouble("vecZ", motion.z);

	}

	@Override
	public void cleanup() {
		super.cleanup();
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void cleanupClient() {
		super.cleanupClient();
	}

	public StormType getType() {
		return type;
	}

	public void setType(StormType type) {
		this.type = type;
	}
}
