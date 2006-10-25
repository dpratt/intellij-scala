package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

object CompositeExpr {
/*
Composite Expression
Default grammar
Expr1 ::=   if �(� Expr1 �)� [NewLine] Expr [[�;�] else Expr]                           (if)
          | try �{� Block �}� [catch �{� CaseClauses �}�] [finally Expr]                 (try)
          | while �(� Expr �)� [NewLine] Expr                                          (while)
          | do Expr [StatementSeparator] while �(� Expr �)�                            (do)
          | for (�(� Enumerators �)� | �{� Enumerators �}�)[NewLine] [yield] Expr        (for)
          | throw Expr                                                               (throw)
          | return [Expr]                                                            (return)
          | [SimpleExpr �.�] id �=� Expr                                               (b2)
          | SimpleExpr ArgumentExprs �=� Expr                                         (b1)
          | PostfixExpr [�:� Type1]                                                   (a)
          | PostfixExpr match �{� CaseClauses �}�
          | MethodClosure
*/

  def parse(builder : PsiBuilder) : ScalaElementType = {
    val compMarker = builder.mark()

    /***********************/
    /**** Various cases ****/
    /***********************/

    /****** case (a) *******/
    def aCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback
      var result = PostfixExpr.parse(builder)
      if (result.equals(ScalaElementTypes.POSTFIX_EXPR)) {
        builder getTokenType match {
          /*    [�:� Type1]   */
          case ScalaTokenTypes.tCOLON => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
            val res = Type1 parse (builder)
            res match {
              case ScalaElementTypes.TYPE1 => {
                rollbackMarker.drop()
                compMarker.done(ScalaElementTypes.EXPR1)
                ScalaElementTypes.EXPR1
              }
              case _ => {
                rollbackMarker.rollbackTo()
                ScalaElementTypes.WRONGWAY
              }
            }
          }
          case _ => {
            rollbackMarker.drop()
            compMarker.done (ScalaElementTypes.EXPR1)
            ScalaElementTypes.EXPR1
          }
        }
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    /****** cases (b1), (b2) *******/
    def bCase: ScalaElementType = {
      var rollbackMarker = builder.mark() // marker to rollback

      def assignProcess: ScalaElementType = {
        ParserUtils.eatElement(builder , ScalaTokenTypes.tASSIGN)
        ParserUtils.rollForward(builder)
        var res = Expr.parse(builder)
        if (res.eq(ScalaElementTypes.EXPR)) {
          rollbackMarker.drop()
          compMarker.done (ScalaElementTypes.EXPR1)
          ScalaElementTypes.EXPR1
        } else {
          rollbackMarker.drop()
          builder.error("Expression expected")
          compMarker.done (ScalaElementTypes.EXPR1)
          ScalaElementTypes.EXPR1
        }
      }

      def processSimpleExpr: ScalaElementType = {
        ParserUtils.rollForward(builder)
        var res = SimpleExpr.parse(builder)
        if (res.parsed.eq(ScalaElementTypes.SIMPLE_EXPR) &&
            ( res.endness.eq("argexprs") || res.endness.eq(".id") ) ) {
          ParserUtils.rollForward(builder)
          if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
          assignProcess
          } else {
            rollbackMarker.rollbackTo()
            ScalaElementTypes.WRONGWAY
          }
        } else {
          rollbackMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.eatElement(builder , ScalaTokenTypes.tIDENTIFIER)
        if (builder.getTokenType.eq(ScalaTokenTypes.tASSIGN)) {
          ParserUtils.rollForward(builder)
          assignProcess
        } else {
          rollbackMarker.rollbackTo()
          rollbackMarker = builder.mark()
          processSimpleExpr
        }
      } else if (builder.getTokenType.eq(ScalaTokenTypes.kTHIS) ||
                 builder.getTokenType.eq(ScalaTokenTypes.kSUPER)){
        processSimpleExpr
      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }


    /** case (if) **/
    def ifCase: ScalaElementType = {
      val rollbackMarker = builder.mark() // marker to rollback

      /* for mistakes processing */
      def errorDone(msg: String): ScalaElementType = {
        rollbackMarker.drop()
        builder.error(msg)
        compMarker.done(ScalaElementTypes.EXPR1)
        ScalaElementTypes.EXPR1
      }

      def elseProcessing: ScalaElementType = {
        if (builder.getTokenType.eq(ScalaTokenTypes.tSEMICOLON)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tSEMICOLON)
          ParserUtils.rollForward(builder)
        }
        if (builder.getTokenType.eq(ScalaTokenTypes.kELSE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kELSE)
          ParserUtils.rollForward(builder)
          val res2 = Expr.parse(builder)
          if (res2.eq(ScalaElementTypes.EXPR)){
            rollbackMarker.drop()
            compMarker.done(ScalaElementTypes.EXPR1)
            ScalaElementTypes.EXPR1
          } else errorDone("Wrong expression")                   
        } else errorDone("else expected")
      }

      if (builder.getTokenType.eq(ScalaTokenTypes.kIF)){
        ParserUtils.eatElement(builder, ScalaTokenTypes.kIF)
        ParserUtils.rollForward(builder)
        if (builder.getTokenType.eq(ScalaTokenTypes.tLPARENTHIS)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
          ParserUtils.rollForward(builder)
          val res = CompositeExpr parse(builder)
          if (res.eq(ScalaElementTypes.EXPR1)){
            ParserUtils.rollForward(builder)
            if (builder.getTokenType.eq (ScalaTokenTypes.tRPARENTHIS)){
              ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
              ParserUtils.rollForward(builder)
              val res1 = Expr.parse(builder)
              if (res1.eq(ScalaElementTypes.EXPR)){
                ParserUtils.rollForward(builder)
                builder.getTokenType match {
                  case ScalaTokenTypes.kELSE | ScalaTokenTypes.tSEMICOLON => elseProcessing
                  case _ => {
                    rollbackMarker.drop()
                    compMarker.done(ScalaElementTypes.EXPR1)
                    ScalaElementTypes.EXPR1
                  }
                }
              } else errorDone("Wrong expression")
            } else errorDone(" ) expected")
          } else errorDone("Wrong expression")
        } else errorDone("( expected")

      } else {
        rollbackMarker.rollbackTo()
        ScalaElementTypes.WRONGWAY
      }
    }

    var result = ScalaElementTypes.WRONGWAY
    /* Various variants of parsing */
    def variants(variantProcessing: => ScalaElementType) : Boolean = {
      result = variantProcessing
      result.equals(ScalaElementTypes.EXPR1)
    }



    /* Parsing function body */
    /* case (if) */
    if (variants(ifCase)) result
    /* cases (b1), (b2) */
    else if (variants(bCase)) result
    /* case (a) */
    else if (variants(aCase)) result
    else {
      compMarker.rollbackTo()
      ScalaElementTypes.WRONGWAY
    }

  }

}