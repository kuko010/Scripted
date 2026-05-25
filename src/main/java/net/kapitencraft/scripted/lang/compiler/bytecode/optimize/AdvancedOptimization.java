package net.kapitencraft.scripted.lang.compiler.bytecode.optimize;

public interface AdvancedOptimization {

    void optimize(BytecodeOptimizer.OptimizationStorage instructions);
}
