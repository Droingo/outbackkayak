package net.droingo.outbackkayak.registry;

import net.droingo.outbackkayak.OutbackKayak;
import net.droingo.outbackkayak.item.KayakItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

public final class ModItems {
    public static final Item KAYAK = Registry.register(
            Registries.ITEM,
            Identifier.of(OutbackKayak.MOD_ID, "kayak"),
            new KayakItem(new Item.Settings().maxCount(1))
    );

    public static final Item PADDLE = Registry.register(
            Registries.ITEM,
            Identifier.of(OutbackKayak.MOD_ID, "paddle"),
            new Item(new Item.Settings().maxCount(1))
    );

    private ModItems() {
    }

    public static void register() {
        OutbackKayak.LOGGER.info("Registering Outback Kayak items.");

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(KAYAK);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(PADDLE);
        });
    }}