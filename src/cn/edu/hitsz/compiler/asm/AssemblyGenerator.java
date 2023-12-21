package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.*;

import java.util.*;
import java.util.stream.Collectors;

import static cn.edu.hitsz.compiler.utils.FileUtils.writeLines;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    
    // 生成的汇编指令列表
    private final List<asmInst> asmInsts = new ArrayList<>();

    //存放经过预处理后的中间指令
    private final List<Instruction> instructions = new ArrayList<>();
    //用于装载某些操作返回的两条指令
    private final List<Instruction> doubleInst = new ArrayList<>();
    private List<Instruction> useInst = new ArrayList<>();

    //寄存器分配表
    private final Map<Register, IRVariable> regMap = new HashMap<>();

    //保存仍要被使用的 IRVariable , 便于寄存器分配时进行判断
    private final List<IRVariable> irVarList = new ArrayList<>();
    public void loadIR(List<Instruction> originInstructions) {
        // TODO: 读入前端提供的中间代码并生成所需要的信息
        for (Instruction instruction : originInstructions) {
            useInst.clear();
            useInst =preProcess(instruction);
            for (Instruction inst:useInst) {
                instructions.add(inst);
            }
        }

        for (Instruction instruction : instructions) {
            irVarList.addAll(getIRVariablesFromInstruction(instruction));
        }
    }
    private List<Instruction> preProcess(Instruction instruction) {
        if (instruction.getKind().isBinary()) {
            return preprocessBinaryInstruction(instruction);
        } else {
            doubleInst.clear();
            doubleInst.add(instruction);
            return doubleInst;
        }
    }

    private List<Instruction> preprocessBinaryInstruction(Instruction instruction) {
        var lhs = instruction.getLHS();
        var rhs = instruction.getRHS();
        var result = instruction.getResult();

        if (lhs.isImmediate() && rhs.isImmediate()) {
            int immediate;
            switch (instruction.getKind()) {
                case SUB -> immediate = ((IRImmediate) lhs).getValue() - ((IRImmediate) rhs).getValue();
                case ADD -> immediate = ((IRImmediate) lhs).getValue() + ((IRImmediate) rhs).getValue();
                default -> immediate = ((IRImmediate) lhs).getValue() * ((IRImmediate) rhs).getValue();
            }
            doubleInst.clear();
            doubleInst.add(Instruction.createMov(result, IRImmediate.of(immediate)));
            return doubleInst;
        } else if (lhs.isImmediate() && rhs.isIRVariable() && (instruction.getKind() == InstructionKind.MUL || instruction.getKind() == InstructionKind.SUB)) {
            var immediate = (IRImmediate) lhs;
            var tempResult = IRVariable.temp();
            var instruction1 = Instruction.createMov(tempResult, immediate);
            Instruction instruction2;
            if (instruction.getKind() == InstructionKind.SUB) {
                    instruction2 = Instruction.createSub(result, tempResult, rhs);
                } else {
                // MUL
                    instruction2 = Instruction.createMul(result, tempResult, rhs);
            }
            doubleInst.clear();
            doubleInst.add(instruction1);
            doubleInst.add(instruction2);
            return doubleInst;
        } else if (lhs.isIRVariable() && rhs.isImmediate() && (instruction.getKind() == InstructionKind.MUL)) {
            var immediate = (IRImmediate) rhs;
            var tempResult = IRVariable.temp();
            var instruction1 = Instruction.createMov(tempResult, immediate);
            var instruction2 = Instruction.createMul( result, lhs, tempResult);
            doubleInst.clear();
            doubleInst.add(instruction1);
            doubleInst.add(instruction2);
            return doubleInst;
        } else {
            doubleInst.clear();
            doubleInst.add(instruction);
            return doubleInst;
        }
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // TODO: 执行寄存器分配与代码生成
        for (Instruction instruction : instructions) {
            processInstruction(instruction);
            removeIRVarList(instruction);
        }
    }

    private void processInstruction(Instruction instruction) {
        if (instruction.getKind().isBinary()) {
            processBinaryInstruction(instruction);
        } else if (instruction.getKind().isReturn()) {
            processReturnInstruction(instruction);
        } else {
            processMovInstruction(instruction);
        }

    }

    private void processBinaryInstruction(Instruction instruction) {
        var lhs = instruction.getLHS();
        var rhs = instruction.getRHS();
        Operand firstOperand = new Operand(allocRegister(instruction));

        switch (instruction.getKind()) {
            case ADD -> processAddInstruction(lhs, rhs, firstOperand);
            case SUB -> processSubInstruction(lhs, rhs, firstOperand);
            case MUL -> processMulInstruction(lhs, rhs, firstOperand);
            default -> throw new RuntimeException("Unknown instruction type!");
        }
    }

    private void processAddInstruction(IRValue lhs, IRValue rhs, Operand firstOperand) {
        if (lhs.isIRVariable() && rhs.isIRVariable()) {
            Operand secondOperand = new Operand(getReg((IRVariable) lhs));
            Operand thirdOperand = new Operand(getReg((IRVariable) rhs));
            asmInst asmInst = new asmInst(Opcode.add, firstOperand, secondOperand, thirdOperand);
            asmInsts.add(asmInst);
        } else {
            Operand secondOperand;
            Operand thirdOperand;
            if (lhs.isImmediate()) {
                secondOperand = new Operand(getReg((IRVariable) rhs));
                thirdOperand = new Operand(((IRImmediate) lhs).getValue());
            } else {
                secondOperand = new Operand(getReg((IRVariable) lhs));
                thirdOperand = new Operand(((IRImmediate) rhs).getValue());
            }
            asmInst asmInst = new asmInst(Opcode.addi, firstOperand, secondOperand, thirdOperand);
            asmInsts.add(asmInst);
        }
    }

    private void processSubInstruction(IRValue lhs, IRValue rhs, Operand firstOperand) {
        if (lhs.isIRVariable() && rhs.isIRVariable()) {
            Operand secondOperand = new Operand(getReg((IRVariable) lhs));
            Operand thirdOperand = new Operand(getReg((IRVariable) rhs));
            asmInst asmInst = new asmInst(Opcode.sub, firstOperand, secondOperand, thirdOperand);
            asmInsts.add(asmInst);
        } else {
            Operand secondOperand = new Operand(getReg((IRVariable) lhs));
            Operand thirdOperand = new Operand(((IRImmediate) rhs).getValue());
            asmInst asmInst = new asmInst(Opcode.subi, firstOperand, secondOperand, thirdOperand);
            asmInsts.add(asmInst);
        }
    }

    private void processMulInstruction(IRValue lhs, IRValue rhs, Operand firstOperand) {
        Operand secondOperand = new Operand(getReg((IRVariable) lhs));
        Operand thirdOperand = new Operand(getReg((IRVariable) rhs));
        asmInst asmInst = new asmInst(Opcode.mul, firstOperand, secondOperand, thirdOperand);
        asmInsts.add(asmInst);
    }

    private void processReturnInstruction(Instruction instruction) {
        Operand firstOperand = new Operand(Register.a0);
        var returnValue = instruction.getReturnValue();
        generateBinaryAsm(firstOperand, returnValue);
    }

    private void processMovInstruction(Instruction instruction) {
        Operand firstOperand = new Operand(allocRegister(instruction));
        var from = instruction.getFrom();
        generateBinaryAsm(firstOperand, from);
    }
    

    private void generateBinaryAsm(Operand firstOperand, IRValue from) {
        if (from.isImmediate()) {
            // li rd, imm
            Operand secondOperand = new Operand(((IRImmediate) from).getValue());
            asmInsts.add(new asmInst(Opcode.li, firstOperand, secondOperand));
        } else {
            // mv rd, rs1
            Operand secondOperand = new Operand(getReg((IRVariable) from));
            asmInsts.add(new asmInst(Opcode.mv, firstOperand, secondOperand));
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        // TODO: 输出汇编代码到文件
        List<String> toFIle = new ArrayList<>();
        toFIle.add(".text");
        for (asmInst asmInst : asmInsts) {
            toFIle.add(asmInst.toString());
        }
        writeLines(path, toFIle);
    }
    private Register allocRegister(Instruction currentInstruction) {

        var result = currentInstruction.getResult();

        // 若当前变量已经在之前被分配寄存器, 直接返回
        if (regMap.containsValue(result)) {
            return getReg(result);
        }

        // 其它指令不能分配寄存器 a0 , 也不能分配 regMap 中已有的寄存器
        for (var registerName : Register.values()) {
            if (!regMap.containsKey(registerName) && (registerName != Register.a0)) {
                regMap.put(registerName, result);
                return registerName;
            }
        }

        // 若当前已无空闲的寄存器, 检测是否有不再被使用的变量(若有则分配存放该变量的寄存器）
        for (var registerName : regMap.keySet()) {
            if (!irVarList.contains(regMap.get(registerName))) {
                regMap.put(registerName, result);
                return registerName;
            }
        }

        // 否则将无法分配寄存器并报错 (实现的是不完备的寄存器分配)
        throw new RuntimeException("No enough registers!");
    }

    /**
     * 用于根据 IRVariable 从 regMap 查找出分配的寄存器
     */
    private Register getReg(IRVariable irVariable) {
        for (var key : regMap.keySet()) {
            if (Objects.equals(regMap.get(key).getName(), irVariable.getName())) {
                return key;
            }
        }
        // 若没找到, 说明当前变量还没有被分配寄存器, 报错
        throw new RuntimeException("register missing!");
    }
    private List<IRVariable> getIRVariablesFromInstruction(Instruction instruction) {
        List<IRVariable> variables = new ArrayList<>();
        List<IRValue> values = new ArrayList<>();
        if (instruction.getKind().isBinary()) {
            // ADD, SUB, MUL
            values.add(instruction.getResult());
            values.add(instruction.getLHS());
            values.add(instruction.getRHS());
        } else if (instruction.getKind().isReturn()) {
            // RET
            values.add(instruction.getReturnValue());
        } else {
            // MOV
            values.add(instruction.getFrom());
            values.add(instruction.getResult());
        }
        for (IRValue irValue : values) {
            if (irValue.isIRVariable()) {
                variables.add((IRVariable) irValue);
            }
        }
        return variables;
    }

    /**
     * 从 irVarList 中去除掉当前指令包含的 IRVariable
     */
    private void removeIRVarList(Instruction instruction) {
        int num = getIRVariablesFromInstruction(instruction).size();
        while (num > 0) {
            irVarList.remove(0);
            num--;
        }
    }


}
//指令
class asmInst {
    private final Opcode opcode;
    private final List<Operand> operands = new ArrayList<>();

    public asmInst(Opcode opcode, Operand... operands) {
        this.opcode = opcode;
        this.operands.addAll(Arrays.asList(operands));
    }

    @Override
    public String toString() {
        String res = "\t" + opcode.toString() + ' ';
        List<String> operandStrings = operands.stream().map(Operand::toString).collect(Collectors.toList());
        res += String.join(", ", operandStrings);
        return res;
    }
}
//寄存器
enum Register {
    t0, t1, t2, t3, t4, t5, t6, a0
}
//操作类型
enum Opcode {
    add, sub, mul, addi, subi, mv, li
}
//操作数
class Operand {
    private Register register;
    private Integer immediate;

    public Operand(Integer immediate) {
        this.immediate = immediate;
    }

    public Operand(Register register) {
        this.register = register;
    }

    @Override
    public String toString() {
        return (register != null) ? register.toString() : immediate.toString();
    }
}
