package net.droingo.outbackkayak.registry;

import net.droingo.outbackkayak.OutbackKayak;
import net.droingo.outbackkayak.block.RapidControllerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

public final class ModBlocks {
    public static final Block RAPID_CONTROLLER = registerBlockWithItem(
            "rapid_controller",
            new RapidControllerBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.WATER_BLUE)
                    .strength(1.5f)
                    .nonOpaque())
    );

    private ModBlocks() {
    }

    public static void register() {
        OutbackKayak.LOGGER.info("Registering Outback Kayak blocks.");

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(RAPID_CONTROLLER);
        });
    }

    private static Block registerBlockWithItem(String name, Block block) {
        Identifier id = Identifier.of(OutbackKayak.MOD_ID, name);

        Block registeredBlock = Registry.register(
                Registries.BLOCK,
                id,
                block
        );

        Registry.register(
                Registries.ITEM,
                id,
                new BlockItem(registeredBlock, new Item.Settings())
        );

        return registeredBlock;
    }
}