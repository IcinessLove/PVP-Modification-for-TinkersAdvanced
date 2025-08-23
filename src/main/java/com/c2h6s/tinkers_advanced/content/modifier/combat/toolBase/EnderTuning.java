package com.c2h6s.tinkers_advanced.content.modifier.combat.toolBase;

import com.c2h6s.etstlib.entity.specialDamageSources.LegacyDamageSource;
import com.c2h6s.etstlib.tool.modifiers.base.BasicFEModifier;
import com.c2h6s.etstlib.util.IToolUuidGetter;
import com.c2h6s.etstlib.util.MathUtil;
import com.c2h6s.etstlib.util.ToolEnergyUtil;
import com.c2h6s.tinkers_advanced.TiAcConfig;
import com.c2h6s.tinkers_advanced.content.entity.PlasmaSlashEntity;
import com.c2h6s.tinkers_advanced.content.item.toolItem.ElectronTunerItem;
import com.c2h6s.tinkers_advanced.content.objects.ToolEnergyProduction;
import com.c2h6s.tinkers_advanced.util.CommonUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.behavior.AttributesModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.ToolDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierTraitHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.shared.TinkerEffects;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.data.ModifierIds;

import java.util.List;
import java.util.function.BiConsumer;

import static com.c2h6s.tinkers_advanced.content.item.toolItem.ElectronTunerItem.*;

public class EnderTuning extends BasicFEModifier implements ModifierTraitHook, AttributesModifierHook, ToolDamageModifierHook, TooltipModifierHook, MeleeHitModifierHook {
    // 添加一个ResourceLocation来存储攻击计数的键
    private static final ResourceLocation KEY_ATTACK_COUNT = new ResourceLocation("tinkers_advanced", "attack_count");
    
    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOL_STATS, ModifierHooks.MODIFIER_TRAITS, 
                ModifierHooks.ATTRIBUTES, ModifierHooks.TOOL_DAMAGE, ModifierHooks.TOOLTIP, 
                ModifierHooks.MELEE_HIT);
    }

    @Override
    public boolean isNoLevels() {
        return true;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void onLeftClickEmpty(IToolStackView tool, ModifierEntry entry, Player player, Level level, EquipmentSlot equipmentSlot) {
        if (!level.isClientSide && TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get() && getMode(tool) == 1 && player.getAttackStrengthScale(0) > 0.8) {
            // 空A也增加攻击计数
            incrementAttackCount(tool, player, level);
        }
    }

    @Override
    public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
        // 无论是否命中都增加攻击计数
        //incrementAttackCount(tool, context.getAttacker(), context.getAttacker().level());
        
        // 原有的效果逻辑
        if (context.getTarget() instanceof LivingEntity living && TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get()) {
            living.forceAddEffect(new MobEffectInstance(TinkerEffects.enderference.get(), 600, 0), context.getAttacker());
        }
    }

    /**
     * 增加攻击计数并检查是否应该释放剑气
     */
    private void incrementAttackCount(IToolStackView tool, LivingEntity attacker, Level level) {
        if (!TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get() || getMode(tool) != 1) {
            return;
        }
        
        // 获取当前攻击计数
        int attackCount = tool.getPersistentData().getInt(KEY_ATTACK_COUNT);
        
        // 增加计数
        attackCount++;
        tool.getPersistentData().putInt(KEY_ATTACK_COUNT, attackCount);
        
        // 检查是否达到3次攻击
        if (attackCount >= 3) {
            // 重置计数
            tool.getPersistentData().putInt(KEY_ATTACK_COUNT, 0);
            
            // 检查能量并释放剑气
            if (ToolEnergyUtil.extractEnergy(tool, TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get(), true) >= TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get()) {
                ToolEnergyUtil.extractEnergy(tool, TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get(), false);
                
                if (attacker instanceof Player player) {
                    PlasmaSlashEntity entity = PlasmaSlashEntity.create(player, ElectronTunerItem.getSlashScale(tool), player.getLookAngle(), (ToolStack) tool);
                    entity.baseDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE) * (0.25f + Math.min(0.75f, 0.25F * tool.getModifierLevel(TinkerModifiers.sweeping.get())));
                    level.addFreshEntity(entity);
                }
            }
        }
    }

    // 其他方法保持不变...
    @Override
    public int onDamageTool(IToolStackView tool, ModifierEntry modifier, int amount, @Nullable LivingEntity holder) {
        int energyCost = ToolEnergyUtil.extractEnergy(tool, amount * TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get(), true);
        if (energyCost >= TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get()) {
            int cancel = energyCost / TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get();
            amount -= cancel;
            ToolEnergyUtil.extractEnergy(tool, cancel * TiAcConfig.COMMON.ELECTRON_TUNER_CONSUMPTION.get(), false);
        }
        return amount;
    }

    @Override
    public void addToolStats(IToolContext iToolContext, ModifierEntry modifierEntry, ModifierStatsBuilder modifierStatsBuilder) {
        super.addToolStats(iToolContext, modifierEntry, modifierStatsBuilder);
        float value = iToolContext.getPersistentData().getFloat(ElectronTunerItem.KEY_ATTACK_DAMAGE);
        if (value - 0.5 != 0) {
            float damageModifier = (float) Math.abs(TiAcConfig.COMMON.ELECTRON_TUNER_ATTACK_DAMAGE_ADJUSTABLE_RANGE.get() * (value - 0.5)) + 1;
            float speedModifier = (float) Math.abs(TiAcConfig.COMMON.ELECTRON_TUNER_ATTACK_SPEED_ADJUSTABLE_RANGE.get() * (value - 0.5)) + 1;
            if (value - 0.5 < 0) {
                ToolStats.ATTACK_SPEED.multiply(modifierStatsBuilder, speedModifier);
                ToolStats.ATTACK_DAMAGE.multiply(modifierStatsBuilder, 1 / damageModifier);
            } else {
                ToolStats.ATTACK_SPEED.multiply(modifierStatsBuilder, 1 / speedModifier);
                ToolStats.ATTACK_DAMAGE.multiply(modifierStatsBuilder, damageModifier);
            }
        }
    }

    @Override
    public LegacyDamageSource modifyDamageSource(IToolStackView tool, ModifierEntry entry, LivingEntity attacker, InteractionHand hand, Entity target, EquipmentSlot sourceSlot, boolean isFullyCharged, boolean isExtraAttack, boolean isCritical, LegacyDamageSource source) {
        if (TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get() && ElectronTunerItem.getMode(tool) == 0)
            return source.setBypassInvulnerableTime();
        return source;
    }

    @Override
    public int getCapacity(ModifierEntry modifierEntry) {
        return 100000;
    }

    @Override
    public void addTraits(IToolContext iToolContext, ModifierEntry modifierEntry, TraitBuilder traitBuilder, boolean b) {
        if (iToolContext.getPersistentData().getFloat(ElectronTunerItem.KEY_ATTACK_DAMAGE) > 0.75 && TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get()) {
            traitBuilder.add(TinkerModifiers.severing.getId(), 3);
        }

        if (iToolContext.getPersistentData().getFloat(ElectronTunerItem.KEY_ATTACK_DAMAGE) <= 0.25 && TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get()) {
            traitBuilder.add(ModifierIds.pierce, 2);
        }
    }

    @Override
    public void addAttributes(IToolStackView tool, ModifierEntry modifierEntry, EquipmentSlot equipmentSlot, BiConsumer<Attribute, AttributeModifier> biConsumer) {
        if (getMode(tool) == 0 && TiAcConfig.COMMON.ELECTRON_TUNER_SPECIAL_BONUS.get()) {
            IToolUuidGetter.getUuid(tool).ifPresent(uuid ->
                    biConsumer.accept(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(uuid, ForgeMod.ENTITY_REACH.get().getDescriptionId(), 0, AttributeModifier.Operation.ADDITION)));

        }
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifierEntry, @Nullable Player player, List<Component> list, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        super.addTooltip(tool, modifierEntry, player, list, tooltipKey, tooltipFlag);
        ToolEnergyProduction production = ToolEnergyProduction.getOrCreate((ToolStack) tool);
        int value = production.lastGeneration;
        long remainedEnergyToGen = production.energyToProduce;
        long remainedEnergyToCost = production.energyToReduce;
        list.add(Component.literal((value > 0 ? "+" : "") + MathUtil.getEnergyString(value) + "/t").withStyle(ChatFormatting.RED));
        list.add(Component.translatable("tooltip.tinkers_advanced.energy_gen_remain").append(CommonUtil.getEnergyString(remainedEnergyToGen)).withStyle(ChatFormatting.RED));
        list.add(Component.translatable("tooltip.tinkers_advanced.energy_cost_remain").append(CommonUtil.getEnergyString(remainedEnergyToCost)).withStyle(ChatFormatting.RED));

        // 在工具提示中显示当前攻击计数
        int attackCount = tool.getPersistentData().getInt(KEY_ATTACK_COUNT);
        list.add(Component.translatable("tooltip.tinkers_advanced.attack_count", attackCount, 3).withStyle(ChatFormatting.GRAY));
    }
}