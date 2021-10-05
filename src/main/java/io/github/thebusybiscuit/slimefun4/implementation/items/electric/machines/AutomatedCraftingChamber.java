package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.events.BlockPlacerPlaceEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.EnhancedCraftingTable;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.Item.CustomItemSerializer;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.Item.CustomItemSerializer.ItemFlag;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

public abstract class AutomatedCraftingChamber extends SlimefunItem implements InventoryBlock, EnergyNetComponent {

    private final int[] border = { 0, 1, 3, 4, 5, 7, 8, 13, 14, 15, 16, 17, 50, 51, 52, 53 };
    private final int[] inputBorder = { 9, 10, 11, 12, 13, 18, 22, 27, 31, 36, 40, 45, 46, 47, 48, 49 };
    private final int[] outputBorder = { 23, 24, 25, 26, 32, 35, 41, 42, 43, 44 };

    private final Map<String, ItemStack> craftingRecipes = new HashMap<>();

    @ParametersAreNonnullByDefault
    public AutomatedCraftingChamber(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        new BlockMenuPreset(getId(), "&6Automated Crafting Chamber") {

            @Override
            public void init() {
                constructMenu(this);
            }

            @Override
            public void newInstance(BlockMenu menu, Block b) {
                if (!BlockStorage.hasBlockInfo(b) || BlockStorage.getLocationInfo(b.getLocation(), "enabled") == null || BlockStorage.getLocationInfo(b.getLocation(), "enabled").equals(String.valueOf(false))) {
                    menu.replaceExistingItem(6, new CustomItemStack(Material.GUNPOWDER, "&7Enabled: &4\u2718", "", "&e> Click to enable this Machine"));
                    menu.addMenuClickHandler(6, (p, slot, item, action) -> {
                        BlockStorage.addBlockInfo(b, "enabled", String.valueOf(true));
                        newInstance(menu, b);
                        return false;
                    });
                } else {
                    menu.replaceExistingItem(6, new CustomItemStack(Material.REDSTONE, "&7Enabled: &2\u2714", "", "&e> Click to disable this Machine"));
                    menu.addMenuClickHandler(6, (p, slot, item, action) -> {
                        BlockStorage.addBlockInfo(b, "enabled", String.valueOf(false));
                        newInstance(menu, b);
                        return false;
                    });
                }

                menu.replaceExistingItem(7, new CustomItemStack(Material.CRAFTING_TABLE, "&7Craft Last", "", "&e> Click to craft the last shaped recipe", "&cOnly works with the last one"));
                menu.addMenuClickHandler(7, (p, slot, item, action) -> {
                    tick(b, true);
                    return false;
                });
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass") || Slimefun.getProtectionManager().hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return getOutputSlots();
                }

                List<Integer> slots = new ArrayList<>();
                for (int slot : getInputSlots()) {
                    if (menu.getItemInSlot(slot) != null) {
                        slots.add(slot);
                    }
                }

                Collections.sort(slots, compareSlots(menu));

                int[] array = new int[slots.size()];

                for (int i = 0; i < slots.size(); i++) {
                    array[i] = slots.get(i);
                }

                return array;
            }
        };
        addItemHandler(onPlace(), onBreak());
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b);

                if (inv != null) {
                    inv.dropItems(b.getLocation(), getInputSlots());
                    inv.dropItems(b.getLocation(), getOutputSlots());
                }
            }
        };
    }

    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(true) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                BlockStorage.addBlockInfo(e.getBlock(), "enabled", String.valueOf(false));
            }

            @Override
            public void onBlockPlacerPlace(BlockPlacerPlaceEvent e) {
                BlockStorage.addBlockInfo(e.getBlock(), "enabled", String.valueOf(false));
            }
        };
    }

    private Comparator<Integer> compareSlots(DirtyChestMenu menu) {
        return (slot1, slot2) -> menu.getItemInSlot(slot1).getAmount() - menu.getItemInSlot(slot2).getAmount();
    }

    protected void constructMenu(BlockMenuPreset preset) {
        for (int i : border) {
            preset.addItem(i, new CustomItemStack(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " "), (p, slot, item, action) -> false);
        }

        for (int i : inputBorder) {
            preset.addItem(i, new CustomItemStack(new ItemStack(Material.BLUE_STAINED_GLASS_PANE), " "), (p, slot, item, action) -> false);
        }

        for (int i : outputBorder) {
            preset.addItem(i, new CustomItemStack(new ItemStack(Material.ORANGE_STAINED_GLASS_PANE), " "), (p, slot, item, action) -> false);
        }

        for (int i : getOutputSlots()) {
            preset.addMenuClickHandler(i, new AdvancedMenuClickHandler() {

                @Override
                public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
                    return false;
                }

                @Override
                public boolean onClick(InventoryClickEvent e, Player p, int slot, ItemStack cursor, ClickAction action) {
                    return cursor == null || cursor.getType() == null || cursor.getType() == Material.AIR;
                }
            });
        }

        preset.addItem(2, new CustomItemStack(new ItemStack(Material.CRAFTING_TABLE), "&eRecipe", "", "&bPut in the Recipe you want to craft", "&4Enhanced Crafting Table Recipes ONLY"), (p, slot, item, action) -> false);
    }

    public abstract int getEnergyConsumption();

    @Override
    public int[] getInputSlots() {
        return new int[] { 19, 20, 21, 28, 29, 30, 37, 38, 39 };
    }

    @Override
    public int[] getOutputSlots() {
        return new int[] { 33, 34 };
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem sf, Config data) {
                AutomatedCraftingChamber.this.tick(b, false);
            }

            @Override
            public boolean isSynchronized() {
                return false;
            }
        });
    }

    protected void tick(Block block, boolean craftLast) {
        if (!craftLast && BlockStorage.getLocationInfo(block.getLocation(), "enabled").equals(String.valueOf(false))) {
            return;
        }

        if (getCharge(block.getLocation()) < getEnergyConsumption()) {
            return;
        }

        String input = getSerializedInput(block, craftLast);
        testInputAgainstRecipes(block, input);
    }

    private String getSerializedInput(Block block, boolean craftLast) {
        BlockMenu menu = BlockStorage.getInventory(block);
        StringBuilder builder = new StringBuilder();
        int i = 0;
        boolean lastIteration = false;

        for (int j = 0; j < 9; j++) {
            if (i > 0) {
                builder.append(" </slot> ");
            }

            ItemStack item = menu.getItemInSlot(getInputSlots()[j]);

            if (item != null && item.getAmount() == 1) {
                if (craftLast) {
                    lastIteration = true;
                } else {
                    return "";
                }
            }

            builder.append(CustomItemSerializer.serialize(item, ItemFlag.MATERIAL, ItemFlag.ITEMMETA_DISPLAY_NAME, ItemFlag.ITEMMETA_LORE));

            i++;
        }

        // we're only executing the last possible shaped recipe
        // we don't want to allow this to be pressed instead of the default timer-based
        // execution to prevent abuse and auto clickers
        if (craftLast && !lastIteration) {
            return "";
        }

        return builder.toString();
    }

    private void testInputAgainstRecipes(Block block, String input) {
        BlockMenu menu = BlockStorage.getInventory(block);

        ItemStack output = craftingRecipes.get(input);
        if (output != null && menu.fits(output, getOutputSlots())) {
            menu.pushItem(output.clone(), getOutputSlots());
            removeCharge(block.getLocation(), getEnergyConsumption());

            for (int j = 0; j < 9; j++) {
                if (menu.getItemInSlot(getInputSlots()[j]) != null) {
                    menu.consumeItem(getInputSlots()[j]);
                }
            }
        }
    }

    public void loadRecipes() {
        EnhancedCraftingTable machine = (EnhancedCraftingTable) SlimefunItems.ENHANCED_CRAFTING_TABLE.getItem();

        for (ItemStack[] inputs : RecipeType.getRecipeInputList(machine)) {
            StringBuilder builder = new StringBuilder();
            int i = 0;

            for (ItemStack item : inputs) {
                if (i > 0) {
                    builder.append(" </slot> ");
                }

                builder.append(CustomItemSerializer.serialize(item, ItemFlag.MATERIAL, ItemFlag.ITEMMETA_DISPLAY_NAME, ItemFlag.ITEMMETA_LORE));

                i++;
            }

            craftingRecipes.put(builder.toString(), RecipeType.getRecipeOutputList(machine, inputs));
        }
    }
}