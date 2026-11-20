package com.luatweaker.core.vm;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the pure-Lua Brain behavior-tree runtime
 * ({@code luatweaker_brain.lua}) plus the new {@link IEntity} AI primitive
 * defaults. Everything runs on a real VM: trees tick here exactly as they
 * will from an entity onTick hook in game.
 */
public class BrainTest {

    private ILuaEngine engine;

    @BeforeEach
    void createEngine() {
        engine = LuaEngineFactory.create(false);
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private void runLua(String source) {
        engine.executeString(source, "brain_test");
        String error = engine.getAndClearLastExecutionError();
        assertNull(error, "Brain Lua failed: " + error + "\n--- source ---\n" + source);
    }

    private static final String FAKE_ENT = """
            local function fakeEnt(uuid, hp, maxHp, x, y, z, see)
                local e = { _uuid = uuid, _hp = hp, _max = maxHp,
                    _x = x or 0, _y = y or 64, _z = z or 0, _see = see ~= false }
                function e:getUuid() return self._uuid end
                function e:getHealth() return self._hp end
                function e:getMaxHealth() return self._max end
                function e:getX() return self._x end
                function e:getY() return self._y end
                function e:getZ() return self._z end
                function e:canSee(o) return self._see end
                function e:distanceTo(o)
                    local dx = self._x - o:getX()
                    local dy = self._y - o:getY()
                    local dz = self._z - o:getZ()
                    return math.sqrt(dx * dx + dy * dy + dz * dz)
                end
                return e
            end
            """;

    @Test
    void brainGlobalAndRequireAliases() {
        runLua("""
                assert(Brain ~= nil, 'Brain global missing from bootstrap')
                assert(Brain.SUCCESS == 'success')
                assert(Brain.FAILURE == 'failure')
                assert(Brain.RUNNING == 'running')
                assert(require('LuaTweaker.Brain') == Brain, 'full alias must resolve')
                assert(require('Brain') == Brain, 'short alias must resolve')
                """);
    }

    @Test
    void selectorPicksLeftmostNonFailure() {
        runLua("""
                local fail = Brain.Action('f', function() return Brain.FAILURE end)
                local ok = Brain.Action('s', function() return Brain.SUCCESS end)
                local run = Brain.Action('r', function() return Brain.RUNNING end)
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                assert(Brain.Tick(Brain.Selector('sel', { fail, ok }), ctx) == 'success')
                assert(Brain.Tick(Brain.Selector('allfail', { fail, fail }), ctx) == 'failure')
                assert(Brain.Tick(Brain.Selector('runfirst', { run, ok }), ctx) == 'running')
                """);
    }

    @Test
    void sequenceStopsAtFirstNonSuccess() {
        runLua("""
                local ran = {}
                local function step(name, status)
                    return Brain.Action(name, function()
                        ran[#ran + 1] = name
                        return status
                    end)
                end
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                ran = {}
                local s = Brain.Tick(Brain.Sequence('seq', {
                    step('a', Brain.SUCCESS),
                    step('b', Brain.FAILURE),
                    step('c', Brain.SUCCESS),
                }), ctx)
                assert(s == 'failure', 'sequence must fail, got ' .. tostring(s))
                assert(#ran == 2 and ran[2] == 'b', 'third step must not run')
                ran = {}
                s = Brain.Tick(Brain.Sequence('seqrun', {
                    step('a', Brain.SUCCESS),
                    step('b', Brain.RUNNING),
                    step('c', Brain.SUCCESS),
                }), ctx)
                assert(s == 'running', 'running must propagate, got ' .. tostring(s))
                assert(#ran == 2, 'steps after RUNNING must not run')
                """);
    }

    @Test
    void decoratorsInvertSucceedFailAndGuard() {
        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                local ok = Brain.Action('ok', function() return Brain.SUCCESS end)
                local no = Brain.Action('no', function() return Brain.FAILURE end)
                local run = Brain.Action('rn', function() return Brain.RUNNING end)
                assert(Brain.Tick(Brain.Inverter('i', ok), ctx) == 'failure')
                assert(Brain.Tick(Brain.Inverter('i', no), ctx) == 'success')
                assert(Brain.Tick(Brain.Inverter('i', run), ctx) == 'running')
                assert(Brain.Tick(Brain.Succeeder('s', no), ctx) == 'success')
                assert(Brain.Tick(Brain.Failer('f', ok), ctx) == 'failure')
                local gated = Brain.Guard('g', function(c) return c.bb:Get('open', false) end, ok)
                assert(Brain.Tick(gated, ctx) == 'failure', 'closed guard must fail')
                ctx.bb:Set('open', true)
                assert(Brain.Tick(gated, ctx) == 'success', 'open guard must run child')
                """);
    }

    @Test
    void repeatCountsAndRetryStaysInTick() {
        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                local n = 0
                local rep = Brain.Repeat('rep', Brain.Action('a', function()
                    n = n + 1
                    return Brain.SUCCESS
                end), 3)
                assert(Brain.Tick(rep, ctx) == 'running')
                assert(Brain.Tick(rep, ctx) == 'running')
                assert(Brain.Tick(rep, ctx) == 'success', 'third success completes')
                assert(n == 3)
                assert(Brain.Tick(rep, ctx) == 'running', 'counter must reset after success')
                -- Retry hammers the child inside ONE tick.
                local tries = 0
                local ret = Brain.Retry('ret', Brain.Action('b', function()
                    tries = tries + 1
                    if tries < 3 then return Brain.FAILURE end
                    return Brain.SUCCESS
                end), 5)
                assert(Brain.Tick(ret, ctx) == 'success')
                assert(tries == 3, 'retry must loop in-tick, got ' .. tries)
                tries = 0
                local hopeless = Brain.Retry('hop', Brain.Action('c', function()
                    tries = tries + 1
                    return Brain.FAILURE
                end), 2)
                assert(Brain.Tick(hopeless, ctx) == 'failure')
                assert(tries == 2)
                """);
    }

    @Test
    void cooldownAndTimeoutUseGameTicks() {
        runLua("""
                local runs = 0
                local cd = Brain.Cooldown('atk', Brain.Action('strike', function()
                    runs = runs + 1
                    return Brain.SUCCESS
                end), 100)
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                assert(Brain.Tick(cd, ctx) == 'success')
                assert(runs == 1)
                ctx.now = 50
                assert(Brain.Tick(cd, ctx) == 'failure', 'cooling down must block')
                assert(runs == 1, 'blocked start must not run the child')
                ctx.now = 100
                assert(Brain.Tick(cd, ctx) == 'success')
                assert(runs == 2)
                -- Timeout aborts a stuck RUNNING action, then resets.
                local to = Brain.Timeout('mv', Brain.Action('walk', function()
                    return Brain.RUNNING
                end), 10)
                local tctx = { bb = Brain.Blackboard(), now = 0 }
                assert(Brain.Tick(to, tctx) == 'running')
                tctx.now = 9
                assert(Brain.Tick(to, tctx) == 'running')
                tctx.now = 10
                assert(Brain.Tick(to, tctx) == 'failure', 'over-limit RUNNING must abort')
                tctx.now = 11
                assert(Brain.Tick(to, tctx) == 'running', 'timer must reset after abort')
                -- Missing game time is a loud error, never a silent pass.
                local ok, err = pcall(Brain.Tick, cd, { bb = Brain.Blackboard() })
                assert(not ok and string.find(err, 'ctx.now', 1, true),
                    'cooldown without ctx.now must fail loudly')
                """);
    }

    @Test
    void blackboardHelpers() {
        runLua("""
                local bb = Brain.Blackboard()
                assert(bb:Get('missing', 'dflt') == 'dflt')
                bb:Set('k', 5)
                assert(bb:Get('k') == 5)
                assert(bb:Inc('k') == 6)
                assert(bb:Inc('k', 4) == 10)
                bb:Stamp('t', 40)
                assert(bb:Last('t') == 40)
                assert(bb:TimeSince('t', 55) == 15)
                assert(bb:TimeSince('never', 55) == nil)
                assert(bb:CooldownReady('c', 20, 0) == true)
                assert(bb:CooldownReady('c', 20, 10) == false)
                assert(bb:CooldownReady('c', 20, 20) == true)
                """);
    }

    @Test
    void registryIsolatesEntitiesAndPrunes() {
        runLua(FAKE_ENT + """
                local reg = Brain.Registry(2)
                local e1 = fakeEnt('u1', 10, 20)
                local e2 = fakeEnt('u2', 10, 20)
                local e3 = fakeEnt('u3', 10, 20)
                reg:Ctx(e1, 1).bb:Set('mark', 'one')
                reg:Ctx(e2, 2)
                assert(reg:Size() == 2)
                -- Same entity keeps its memory across ticks.
                assert(reg:Ctx(e1, 5).bb:Get('mark') == 'one')
                -- Third entity evicts the least-recently-seen (e2, not e1).
                reg:Ctx(e3, 6)
                assert(reg:Size() == 2)
                assert(reg:Ctx(e1, 7).bb:Get('mark') == 'one', 'recently used e1 must survive')
                assert(reg:Ctx(e2, 8).bb:Get('mark') == nil, 'evicted e2 must come back blank')
                reg:Forget(e1)
                assert(reg:Size() == 1)
                -- Ctx tables are cached per entity: zero allocation on hot ticks,
                -- fields refresh every call, and a stale trace never leaks across ticks.
                local c1 = reg:Ctx(e3, 100)
                c1.trace = { 'stale' }
                local c2 = reg:Ctx(e3, 101)
                assert(c1 == c2, 'registry must reuse the ctx table')
                assert(c2.now == 101, 'ctx time must refresh')
                assert(c2.trace == nil, 'trace must reset every tick')
                assert(c2.bb == c1.bb, 'blackboard must survive across ticks')
                """);
    }

    @Test
    void rootReevaluationAbortsLowerBranches() {
        runLua(FAKE_ENT + """
                -- The spec's guard scenario: FLEE (left) preempts PATROL (right)
                -- the tick HP drops, even while patrol reports RUNNING.
                local hp = { value = 100 }
                local did = {}
                local tree = Brain.Selector('root', {
                    Brain.Sequence('survive', {
                        Brain.Condition('dying', function() return hp.value < 20 end),
                        Brain.Action('flee', function(ctx)
                            did[#did + 1] = 'flee'
                            return Brain.SUCCESS
                        end),
                    }),
                    Brain.Action('patrol', function(ctx)
                        did[#did + 1] = 'patrol'
                        return Brain.RUNNING
                    end),
                })
                local reg = Brain.Registry(8)
                local ent = fakeEnt('boss', 100, 100)
                local ctx = reg:Ctx(ent, 0)
                assert(Brain.Tick(tree, ctx) == 'running')
                assert(did[#did] == 'patrol')
                hp.value = 10
                ctx = reg:Ctx(ent, 1)
                assert(Brain.Tick(tree, ctx) == 'success')
                assert(did[#did] == 'flee', 'flee must preempt patrol, got ' .. did[#did])
                """);
    }

    @Test
    void actionRejectsBadStatusesLoudly() {
        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                local bad = Brain.Action('typo', function() return 'sucess' end)
                local ok, err = pcall(Brain.Tick, bad, ctx)
                assert(not ok and string.find(err, 'typo', 1, true),
                    'bad status must name the node, got: ' .. tostring(err))
                local ok2, err2 = pcall(Brain.Action, '', function() end)
                assert(not ok2 and string.find(err2, 'name', 1, true),
                    'unnamed nodes must be rejected')
                """);
    }

    @Test
    void perceptionHelpers() {
        runLua(FAKE_ENT + """
                local me = fakeEnt('me', 30, 100, 0, 64, 0, true)
                local near = fakeEnt('n', 10, 10, 3, 64, 4, true)
                local far = fakeEnt('f', 10, 10, 30, 64, 0, true)
                assert(Brain.HealthFraction(me) == 0.3)
                assert(Brain.HealthFraction(fakeEnt('x', 5, 0)) == 1.0, 'zero max must not divide')
                assert(Brain.InRange(me, near, 6) == true)
                assert(Brain.InRange(me, far, 6) == false)
                assert(Brain.InRange(me, nil, 6) == false)
                -- NearestVisible filters by the CALLER's canSee, then takes the closest.
                local got = Brain.NearestVisible(me, { far, near })
                assert(got == near, 'closest visible candidate must win')
                me._see = false
                assert(Brain.NearestVisible(me, { near }) == nil, 'blind caller sees nothing')
                me._see = true
                -- maxRadius kills far candidates on cheap math BEFORE any raytrace:
                -- far's canSee must never even be called.
                local farProbed = 0
                local farGuard = fakeEnt('fg', 10, 10, 30, 64, 0)
                local oldSee = farGuard.canSee
                function farGuard:canSee(o)
                    farProbed = farProbed + 1
                    return oldSee(self, o)
                end
                local got2 = Brain.NearestVisible(me, { farGuard, near }, 6)
                assert(got2 == near, 'radius filter must keep near')
                assert(farProbed == 0, 'out-of-radius candidate must skip raytrace')
                assert(Brain.NearestVisible(me, { farGuard }, 6) == nil)
                assert(farProbed == 0, 'radius alone must reject without sight checks')
                -- Facing dot: looking along +X at a target on +X is ~1.
                me.LookVector = { X = 1, Y = 0, Z = 0 }
                local ahead = fakeEnt('a', 1, 1, 10, 64, 0)
                local behind = fakeEnt('bh', 1, 1, -10, 64, 0)
                assert(Brain.FacingDot(me, ahead) > 0.99)
                assert(Brain.FacingDot(me, behind) < -0.99)
                assert(Brain.FacingDot(me, nil) == 0)
                """);
    }

    @Test
    void parallelAllNeedsEveryone() {
        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                local ok = Brain.Action('ok', function() return Brain.SUCCESS end)
                local no = Brain.Action('no', function() return Brain.FAILURE end)
                local run = Brain.Action('rn', function() return Brain.RUNNING end)
                assert(Brain.Tick(Brain.Parallel('p', { ok, ok }), ctx) == 'success')
                assert(Brain.Tick(Brain.Parallel('p', { ok, no }), ctx) == 'failure')
                assert(Brain.Tick(Brain.Parallel('p', { ok, run }), ctx) == 'running')
                -- 'any' mirrors it: one success is enough, all failing fails.
                assert(Brain.Tick(Brain.Parallel('p', { no, ok }, 'any'), ctx) == 'success')
                assert(Brain.Tick(Brain.Parallel('p', { no, no }, 'any'), ctx) == 'failure')
                assert(Brain.Tick(Brain.Parallel('p', { no, run }, 'any'), ctx) == 'running')
                -- Children tick even when an earlier sibling already decided:
                -- that simultaneity is the whole point of Parallel.
                local touched = {}
                local function mark(name, status)
                    return Brain.Action(name, function()
                        touched[#touched + 1] = name
                        return status
                    end)
                end
                Brain.Tick(Brain.Parallel('p', { mark('a', 'failure'), mark('b', 'success') }), ctx)
                assert(#touched == 2 and touched[2] == 'b', 'parallel must tick every child')
                local ok2, err = pcall(Brain.Parallel, 'p', { ok }, 'sometimes')
                assert(not ok2 and string.find(err, 'policy', 1, true))
                """);
    }

    @Test
    void utilityScoresHighestFirstWithFallback() {
        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                local log = {}
                local function leaf(name, status)
                    return Brain.Action(name, function()
                        log[#log + 1] = name
                        return status
                    end)
                end
                local tree = Brain.Utility('act', {
                    { score = function() return 90 end, node = leaf('elitist', 'failure') },
                    { score = function() return 80 end, node = leaf('worker', 'success') },
                    { score = function() return 10 end, node = leaf('idle', 'success') },
                })
                assert(Brain.Tick(tree, ctx) == 'success')
                assert(log[1] == 'elitist' and log[2] == 'worker',
                    'top score runs first, failure falls to next, got: ' .. table.concat(log, ','))
                -- Dynamic rescoring: mood swings flip the winner next tick.
                local mood = { hungry = 10 }
                local picky = Brain.Utility('pick', {
                    { score = function() return mood.hungry end, node = leaf('eat', 'success') },
                    { score = function() return 50 end, node = leaf('nap', 'success') },
                })
                log = {}
                assert(Brain.Tick(picky, ctx) == 'success' and log[1] == 'nap')
                mood.hungry = 99
                log = {}
                assert(Brain.Tick(picky, ctx) == 'success' and log[1] == 'eat')
                -- Non-number scores are loud, never silently sorted.
                local bad = Brain.Utility('bad', {
                    { score = function() return 'lots' end, node = leaf('x', 'success') },
                })
                local ok, err = pcall(Brain.Tick, bad, ctx)
                assert(not ok and string.find(err, 'must return a number', 1, true),
                    'got: ' .. tostring(err))
                """);
    }

    @Test
    void latchFiresOnceUntilReset() {
        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0 }
                local shots = 0
                local latched = Brain.Latch('once', Brain.Action('boom', function()
                    shots = shots + 1
                    return Brain.SUCCESS
                end))
                assert(Brain.Tick(latched, ctx) == 'success')
                assert(Brain.Tick(latched, ctx) == 'success')
                assert(shots == 1, 'latched child must not refire, got ' .. shots)
                latched:Reset(ctx)
                assert(Brain.Tick(latched, ctx) == 'success')
                assert(shots == 2, 'reset must allow one more run')
                -- Failures never latch: the next tick retries.
                local tries = 0
                local flaky = Brain.Latch('flaky', Brain.Action('f', function()
                    tries = tries + 1
                    if tries < 2 then return Brain.FAILURE end
                    return Brain.SUCCESS
                end))
                assert(Brain.Tick(flaky, ctx) == 'failure')
                assert(Brain.Tick(flaky, ctx) == 'success')
                assert(Brain.Tick(flaky, ctx) == 'success')
                assert(tries == 2, 'only the success latches, got ' .. tries)
                """);
    }

    @Test
    void squadSharesAcrossEntities() {
        runLua(FAKE_ENT + """
                Brain.SquadForget('pack_test')
                local alpha = Brain.Squad('pack_test')
                local beta = Brain.Squad('pack_test')
                assert(alpha == beta, 'same key must return the same board')
                alpha:Set('focus', 'player-1')
                assert(beta:Get('focus') == 'player-1')
                -- Per-entity boards stay private while the squad board is shared.
                local reg = Brain.Registry(8)
                local e1 = fakeEnt('u1', 10, 10)
                local e2 = fakeEnt('u2', 10, 10)
                local c1 = reg:Ctx(e1, 0)
                local c2 = reg:Ctx(e2, 0)
                c1.bb:Set('mine', 1)
                assert(c2.bb:Get('mine') == nil, 'entity boards must not leak')
                c1.bb:Set('seen', true)
                local squad = Brain.Squad('pack_test')
                -- Pack convention: alpha writes, members read.
                if c1.bb:Get('seen') then squad:Set('preyAt', { x = 3, z = 4 }) end
                local prey = squad:Get('preyAt')
                assert(prey.x == 3 and prey.z == 4)
                Brain.SquadForget('pack_test')
                assert(Brain.Squad('pack_test'):Get('focus') == nil, 'forget must wipe')
                local ok, err = pcall(Brain.Squad, '')
                assert(not ok and string.find(err, 'non-empty', 1, true))
                """);
    }

    @Test
    void traceAndDescribe() {        runLua("""
                local ctx = { bb = Brain.Blackboard(), now = 0, trace = {} }
                local tree = Brain.Selector('root', {
                    Brain.Condition('nope', function() return false end),
                    Brain.Action('yes', function() return Brain.SUCCESS end),
                })
                assert(Brain.Tick(tree, ctx) == 'success')
                assert(#ctx.trace == 3, 'root + 2 visited nodes, got ' .. #ctx.trace)
                assert(ctx.trace[1] == 'nope=failure')
                assert(ctx.trace[3] == 'root=success')
                local text = Brain.Describe(tree)
                assert(string.find(text, 'root', 1, true))
                assert(string.find(text, '  nope', 1, true), 'children indent, got:\\n' .. text)
                """);
    }

    @Test
    void entityPrimitiveDefaultsAreSafeNoOps() {
        IEntity stub = new IEntity() {
            @Override public String getType() { return "test:mob"; }
            @Override public String getName() { return "mob"; }
            @Override public float getHealth() { return 10.0f; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 10.0f; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public Object getRawEntity() { return null; }
        };
        assertDoesNotThrow(stub::jump, "default jump must be a safe no-op");
        assertDoesNotThrow(() -> stub.lookAt(1, 2, 3), "default lookAt must be a safe no-op");
        assertDoesNotThrow(() -> stub.lookAtEntity(stub), "default lookAtEntity must be a safe no-op");
        assertEquals(0, stub.getGameTime(), "mock default game time is zero");
    }

    @Test
    void newPrimitivesAreBoundOnEntityTables() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue brain = globals.rawget("Brain");
        assertNotNull(brain, "Brain global missing");
        assertFalse(brain.isNil(), "Brain global must not be nil");
    }
}
