package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import java.util.Stack;
// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    //符号表
    private SymbolTable symbolTable;


     // 语义栈
    private final Stack<SemanticStackEntry> semanticStack = new Stack<>();
    @Override
    public void whenAccept(Status currentStatus) {
        // TODO: 该过程在遇到 Accept 时要采取的代码动作
//        throw new NotImplementedException();
        //貌似不用管
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO: 该过程在遇到 reduce production 时要采取的代码动作
        switch (production.index()) {
            case 4 -> {
                // S -> D id
                // 获得 D id
                var token = semanticStack.pop().getToken();
                var type = semanticStack.pop().getType();
                // 获得Text
                var idText = token.getText();
                symbolTable.get(idText).setType(type);
                // 压入空记录占位
                semanticStack.push(new SemanticStackEntry());
            }
            case 5 -> {
                // D -> int
                var token = semanticStack.pop().getToken();
                var tokenKindID = token.getKindId();
                if ("int".equals(tokenKindID)) {
                    semanticStack.push(new SemanticStackEntry(SourceCodeType.Int));
                } else {
                    throw new RuntimeException("analysis error!");
                }
            }
            default -> {
                int num = production.body().size();
                while (num > 0) {
                    semanticStack.pop();
                    num--;
                }
                // 压入空记录占位
                semanticStack.push(new SemanticStackEntry());
            }
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO: 该过程在遇到 shift 时要采取的代码动作
        semanticStack.push(new SemanticStackEntry(currentToken));
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO: 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        this.symbolTable = table;
    }
}
//实在是想不出来更好的写法了
class SemanticStackEntry {
    private final Token token;
    private final SourceCodeType type;

    public SemanticStackEntry(Token token) {
        this.token = token;
        this.type = null;
    }

    public SemanticStackEntry(SourceCodeType type) {
        this.token = null;
        this.type = type;
    }

    public SemanticStackEntry() {
        this.token = null;
        this.type = null;
    }

    public Token getToken() {
        if (this.token == null) {
            throw new RuntimeException("problem getToken!");
        }
        return this.token;
    }

    public SourceCodeType getType() {
        if (this.type == null) {
            throw new RuntimeException("problem getType!");
        }
        return this.type;
    }

}
