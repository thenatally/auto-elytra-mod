package net.tally.elytraequip;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TallyElytraAutoEquip implements ModInitializer {
	public static final String MOD_ID = "tally-elytra-auto-equip";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
	}
}