package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Reflection-only adapter for Fowl Play 1.21.1 + SmartBrainLib.
 *
 * <p>Skyforge uses the existing hawk entity, brain and flight navigation. The only injected brain
 * object is an empty SOAR activity marker; navigation targets remain ordinary Minecraft Mob
 * navigation calls issued by the Wave C6 controller.
 */
final class SkyforgeFowlPlayHawkBridge {
    final class HawkHandle {
        private final Mob hawk;
        private final Object brain;
        private final Object stockSchedule;
        private final Object thermalSchedule;
        private final Method setSchedule;
        private final Method startFlying;
        private boolean thermalScheduleActive;

        private HawkHandle(
                Mob hawk,
                Object brain,
                Object stockSchedule,
                Object thermalSchedule,
                Method setSchedule,
                Method startFlying) {
            this.hawk = hawk;
            this.brain = brain;
            this.stockSchedule = stockSchedule;
            this.thermalSchedule = thermalSchedule;
            this.setSchedule = setSchedule;
            this.startFlying = startFlying;
        }

        void useThermalSchedule(boolean enabled) throws ReflectiveOperationException {
            if (thermalScheduleActive == enabled) {
                return;
            }
            setSchedule.invoke(brain, enabled ? thermalSchedule : stockSchedule);
            thermalScheduleActive = enabled;
            if (!enabled) {
                hawk.getNavigation().stop();
            }
        }

        void flyToward(double x, double y, double z) throws ReflectiveOperationException {
            startFlying.invoke(hawk);
            hawk.getNavigation().moveTo(x, y, z, 1.0);
        }
    }

    private final Class<?> smartBrainClass;
    private final Constructor<?> activityGroupConstructor;
    private final Method addActivity;
    private final Constructor<?> extendedScheduleConstructor;
    private final Method activityAt;
    private final Activity soar;

    private SkyforgeFowlPlayHawkBridge(
            Class<?> smartBrainClass,
            Constructor<?> activityGroupConstructor,
            Method addActivity,
            Constructor<?> extendedScheduleConstructor,
            Method activityAt,
            Activity soar) {
        this.smartBrainClass = smartBrainClass;
        this.activityGroupConstructor = activityGroupConstructor;
        this.addActivity = addActivity;
        this.extendedScheduleConstructor = extendedScheduleConstructor;
        this.activityAt = activityAt;
        this.soar = soar;
    }

    static SkyforgeFowlPlayHawkBridge create() throws ReflectiveOperationException {
        Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.core.SmartBrain");
        Class<?> activityGroup = Class.forName("net.tslat.smartbrainlib.api.core.BrainActivityGroup");
        Class<?> brainUtils = Class.forName("net.tslat.smartbrainlib.util.BrainUtils");
        Class<?> extendedSchedule =
                Class.forName("aqario.fowlplay.common.entity.ai.brain.ExtendedSchedule");
        Class<?> activities = Class.forName("aqario.fowlplay.core.FPActivities");

        Field soarField = activities.getField("SOAR");
        Object soarSupplier = soarField.get(null);
        if (!(soarSupplier instanceof Supplier<?> supplier) || !(supplier.get() instanceof Activity soar)) {
            throw new ReflectiveOperationException("Fowl Play FPActivities.SOAR did not supply Activity");
        }

        return new SkyforgeFowlPlayHawkBridge(
                smartBrain,
                activityGroup.getConstructor(Activity.class),
                brainUtils.getMethod("addActivity", Brain.class, activityGroup),
                extendedSchedule.getConstructor(),
                extendedSchedule.getMethod("activityAt", int.class, Activity.class),
                soar);
    }

    HawkHandle adapt(Mob hawk) throws ReflectiveOperationException {
        Brain<?> brain = hawk.getBrain();
        if (!smartBrainClass.isInstance(brain)) {
            throw new ReflectiveOperationException(
                    "Fowl Play hawk brain is not SmartBrainLib SmartBrain: " + brain.getClass().getName());
        }

        Object stockSchedule = brain.getClass().getMethod("getSchedule").invoke(brain);
        if (stockSchedule == null) {
            throw new ReflectiveOperationException("Fowl Play hawk has no raptor schedule");
        }

        Object soarGroup = activityGroupConstructor.newInstance(soar);
        addActivity.invoke(null, brain, soarGroup);

        Object thermalSchedule = extendedScheduleConstructor.newInstance();
        addScheduleEntry(thermalSchedule, 0, Activity.IDLE);
        addScheduleEntry(thermalSchedule, 1_000, soar);
        addScheduleEntry(thermalSchedule, 6_000, Activity.IDLE);
        addScheduleEntry(thermalSchedule, 8_000, soar);
        addScheduleEntry(thermalSchedule, 11_000, Activity.IDLE);
        addScheduleEntry(thermalSchedule, 13_000, Activity.REST);
        addScheduleEntry(thermalSchedule, 23_000, Activity.IDLE);

        Class<?> scheduleBase =
                Class.forName("net.tslat.smartbrainlib.api.core.schedule.SmartBrainSchedule");
        Method setSchedule = brain.getClass().getMethod("setSchedule", scheduleBase);
        Method startFlying = hawk.getClass().getMethod("startFlying");

        return new HawkHandle(hawk, brain, stockSchedule, thermalSchedule, setSchedule, startFlying);
    }

    private void addScheduleEntry(Object schedule, int tick, Activity activity)
            throws ReflectiveOperationException {
        activityAt.invoke(schedule, tick, activity);
    }
}
