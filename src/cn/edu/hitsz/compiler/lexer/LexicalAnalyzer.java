package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;



/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {

    private final SymbolTable symbolTable;
    private static List<Token> tokens = new ArrayList<>();//结果

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    private static String text;//读取的完整文本
    private static int begin;//首指针
    private static int check;//指针

    private static final List<Character> blank = Arrays.asList(' ', '\t', '\r', '\n');
    //

    public void loadFile(String path) {
        // TODO: 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
         text = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // TODO: 自动机实现的词法分析过程
        int fileLength=text.length(); //读取长度

        char locate = text.charAt(check);
        while(check < fileLength) {//跳过空白
            locate = text.charAt(check);
            while (locate == ' ' || locate == '\n' || locate == '\t' || locate == '\r') {
                check += 1;  //向后扫描
                locate = text.charAt(check);
            }
            if (Character.isAlphabetic(locate)) {//开始扫描字母
                check += 1; //向后扫描
                String loop = "";
                loop = loop + locate;
                locate = text.charAt(check);
                while (Character.isAlphabetic(locate)) {
                    loop = loop + locate;
                    check += 1;
                    locate = text.charAt(check);
                }
                check -= 1; //回退
                //关键字判断
                if ("int".equals(loop)) {
                    tokens.add(Token.simple("int"));
                } else if ("return".equals(loop)) {
                    tokens.add(Token.simple("return"));
                } else {
                    tokens.add(Token.normal("id", loop));
                    if (!symbolTable.has(loop)) {
                        symbolTable.add(loop);
                    }
                }
            } else if (Character.isDigit(locate)) {//第一个是数字
                String loop = "";
                loop = loop + locate;
                check += 1;
                locate = text.charAt(check);
                while (Character.isDigit(locate)) {
                    loop = loop + locate;
                    check += 1;
                    locate = text.charAt(check);
                }
                tokens.add(Token.normal("IntConst", loop));
                check -= 1;
            } else {
                //标识符
                switch (locate) {
                    case '=':
                        check++;
                        locate = text.charAt(check);
                        if (locate == '=') {
                            tokens.add(Token.simple("=="));
                        } else {
                            tokens.add(Token.simple("="));
                        }
                        break;
                    case ',':
                        tokens.add(Token.simple(","));
                        break;
                    case ';':
                        tokens.add(Token.simple("Semicolon"));
                        break;
                    case '+':
                        tokens.add(Token.simple("+"));
                        break;
                    case '-':
                        tokens.add(Token.simple("-"));
                        break;
                    case '*':
                        tokens.add(Token.simple("*"));
                        break;
                    case '/':
                        tokens.add(Token.simple("/"));
                        break;
                    case '(':
                        tokens.add(Token.simple("("));
                        break;
                    case ')':
                        tokens.add(Token.simple(")"));
                        break;
                    default: break;
                }
            }
            check += 1;
        }
        tokens.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // TODO: 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        return  tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }


}
