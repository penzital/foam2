/**
 * @license
 * Copyright 2017 The FOAM Authors. All Rights Reserved.
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package foam.parse;

import foam.core.AbstractDatePropertyInfo;
import foam.core.ClassInfo;
import foam.core.PropertyInfo;
import foam.lib.json.AnyParser;
import foam.lib.json.IntParser;
import foam.lib.json.KeyParser;
import foam.lib.json.Whitespace;
import foam.lib.parse.*;
import foam.lib.parse.Not;
import foam.lib.parse.Optional;
import foam.lib.query.*;
import foam.mlang.Constant;
import foam.mlang.Expr;
import foam.mlang.predicate.*;
import foam.nanos.auth.Subject;
import foam.nanos.auth.User;
import foam.nanos.logger.Logger;
import foam.util.SafetyUtil;

import java.util.*;

public class QueryParser
{
  protected ClassInfo info_;
  protected List expressions;

  public QueryParser(ClassInfo classInfo) {
    info_ = classInfo;

    List<PropertyInfo>         properties  = classInfo.getAxiomsByClass(PropertyInfo.class);
    expressions = new ArrayList();
    Collections.sort(properties, Collections.reverseOrder());

    for ( PropertyInfo prop : properties ) {
      expressions.add(new LiteralIC(prop.getName(), prop));

      if ( ! SafetyUtil.isEmpty(prop.getShortName()) ) {
        expressions.add(new LiteralIC(prop.getShortName(), prop));
      }

      if ( prop.getAliases().length != 0 ) {
        for ( int i = 0; i < prop.getAliases().length; i++) {
          expressions.add(new LiteralIC(prop.getAliases()[0], prop));
        }
      }
    }
  }

  public PStream parse(PStream ps, ParserContext x) {
    x = x.sub();
    x.set("classInfo", info_);

    return getGrammar().parse(ps, x, "");
  }

  private Grammar getGrammar() {
    Grammar grammar = new Grammar();
    grammar.addSymbol("START", grammar.sym("OR"));


    grammar.addSymbol("FIELD_NAME", new Alt(expressions));

    grammar.addSymbol("OR",
      new Repeat(
        grammar.sym("AND"),
        new Alt(
          new LiteralIC(" OR "),
          Literal.create(" | ")
        ),
      1)
    );
    grammar.addAction("OR", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Object[] values = (Object[])val;

        foam.mlang.predicate.Or or = new foam.mlang.predicate.Or();

        foam.mlang.predicate.Predicate[] args = new foam.mlang.predicate.Predicate[values.length];

        for ( int i = 0 ; i < args.length ; i++ ) {
          args[i] = (foam.mlang.predicate.Predicate)values[i];
        }

        or.setArgs(args);
        return or;
      }
    });

    grammar.addSymbol("AND",
      new Repeat(
        grammar.sym("EXPR"),
        new Alt(new LiteralIC(" AND "), Literal.create(" ")),
      1));
    grammar.addAction("AND", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        foam.mlang.predicate.And and = new foam.mlang.predicate.And();

        Object[] valArr = (Object[]) val;

        foam.mlang.predicate.Predicate[] args = new foam.mlang.predicate.Predicate[valArr.length];

        for ( int i = 0 ; i < valArr.length ; i++ ) {
          args[i] = (foam.mlang.predicate.Predicate) valArr[i];
        }

        and.setArgs(args);
        return and;
      }
    });

    grammar.addSymbol("EXPR", new Alt(grammar.sym("PAREN"),
      grammar.sym("NEGATE"), grammar.sym("HAS"), grammar.sym("IS"), grammar.sym("EQUALS"),
      grammar.sym("BEFORE"), grammar.sym("AFTER"), grammar.sym("ID")));

    grammar.addSymbol("PAREN", new Seq1(1,
      Literal.create("("),
      grammar.sym("OR"),
      Literal.create(")")));

    grammar.addSymbol("NEGATE", new Alt(new Seq1(1,Literal.create("-"),
      grammar.sym("EXPR")),
      new Seq1(1,new LiteralIC("NOT "),grammar.sym("EXPR"))
    ));
    grammar.addAction("NEGATE", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        foam.mlang.predicate.Not predicate = new foam.mlang.predicate.Not();
        predicate.setArg1((Predicate) val);
        return predicate;
      }
    });

    grammar.addSymbol("ID", grammar.sym("NUMBER"));
    grammar.addAction("ID", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Object[] values = (Object[]) val;
        if ( values.length == 0 ) return val;
        Eq predicate = new Eq();
        predicate.setArg1((PropertyInfo) info_.getAxiomByName("ID"));
        predicate.setArg2((Expr) values[0]);
        return predicate;
      }
    });

    grammar.addSymbol("HAS", new Seq1(1, Literal.create("has:"),
      grammar.sym("FIELD_NAME")));
    grammar.addAction("HAS", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Has predicate = new foam.mlang.predicate.Has();
        predicate.setArg1((PropertyInfo) val);
        return predicate;
      }
    });

    grammar.addSymbol("IS", new Seq1(1,Literal.create("is:"), grammar.sym("FIELD_NAME")));
    grammar.addAction("IS", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        foam.mlang.predicate.Binary predicate = new foam.mlang.predicate.Eq ();
        predicate.setArg1((PropertyInfo) val);
        predicate.setArg2(new foam.mlang.Constant(true));
        return predicate;
      }
    });

    grammar.addSymbol("EQUALS", new Seq(
      grammar.sym("FIELD_NAME"),
      new Alt(Literal.create(":"), Literal.create("=")),
      grammar.sym("VALUE_LIST")
    ));
    grammar.addAction("EQUALS", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Object[] values = (Object[]) val;
        Expr prop = ( foam.mlang.Expr ) values[0];

        Object[] value = (Object[]) values[2];
        if ( value[0] instanceof Date || prop instanceof AbstractDatePropertyInfo) {
          And and = new And();
          Gte gte = new Gte();
          gte.setArg1(prop);
          gte.setArg2((value[0] instanceof foam.mlang.Expr) ? (foam.mlang.Expr) value[0] :
            new foam.mlang.Constant(value[0]));
          Lte lte = new Lte();
          lte.setArg1(prop);
          lte.setArg2((value[0] instanceof foam.mlang.Expr) ? (foam.mlang.Expr) value[0] :
            new foam.mlang.Constant(value[0]));

          foam.mlang.predicate.Binary[] predicates = { gte, lte };
          and.setArgs(predicates);
          return and;
        }
        if ( value.length > 1 ) {

          foam.mlang.predicate.Or innerPredicate = new foam.mlang.predicate.Or();

          foam.mlang.predicate.Predicate[] args = new foam.mlang.predicate.Predicate[value.length];
          for ( int i = 0; i < args.length; i++ ) {
            foam.mlang.predicate.Eq eq = new foam.mlang.predicate.Eq();
            eq.setArg1(prop);
            eq.setArg2(( value[i] instanceof foam.mlang.Expr ) ?
              ( foam.mlang.Expr ) value[i] : new foam.mlang.Constant (value[i]));
            args[i] = eq;
          }
          innerPredicate.setArgs(args);

          return innerPredicate;
        }

        if ( values[1].equals(":") ) {
          Contains contains = new Contains();
          contains.setArg1(prop);
          contains.setArg2(( value[0] instanceof foam.mlang.Expr ) ?
            ( foam.mlang.Expr ) value[0] : new foam.mlang.Constant (value[0]));
          foam.mlang.predicate.Binary[] predicates = { contains };
          return contains;
        }

        foam.mlang.predicate.Binary expr = new foam.mlang.predicate.Eq();
        expr.setArg1(prop);
        expr.setArg2(
          ( value[0] instanceof foam.mlang.Expr ) ? (foam.mlang.Expr) value[0] : new foam.mlang.Constant(value[0]));

        return expr;
      }
    });

    grammar.addSymbol("BEFORE", new Seq(
      grammar.sym("FIELD_NAME"),
      new Alt(Literal.create("<="), Literal.create("<"), new LiteralIC("-before:")),
      grammar.sym("VALUE")
    ));
    grammar.addAction("BEFORE", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Object[] values = (Object[]) val;
        foam.mlang.predicate.Binary predicate = values[1].equals("<") ?
          new foam.mlang.predicate.Lt() : new foam.mlang.predicate.Lte();
        predicate.setArg1((foam.mlang.Expr) values[0]);

        predicate
          .setArg2((values[2] instanceof foam.mlang.Expr) ? (foam.mlang.Expr) values[2] :
            new foam.mlang.Constant(values[2]));

        return predicate;
      }
    });

    grammar.addSymbol("AFTER", new Seq(
      grammar.sym("FIELD_NAME"),
      new Alt(Literal.create(">="), Literal.create(">"), new LiteralIC("-after:")),
      grammar.sym("VALUE")
    ));
    grammar.addAction("AFTER", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Object[] values = (Object[]) val;
        foam.mlang.predicate.Binary predicate = values[1].equals(">") ?
          new foam.mlang.predicate.Gt() : new foam.mlang.predicate.Gte();
        predicate.setArg1(( foam.mlang.Expr ) values[0]);

        predicate
          .setArg2(( values[2] instanceof foam.mlang.Expr ) ? (foam.mlang.Expr) values[2] : new foam.mlang.Constant(values[2]));

        return predicate;
      }
    });

    grammar.addSymbol("VALUE", new Alt(grammar.sym("ME"),grammar.sym("NUMBER"),
      grammar.sym("DATE"), grammar.sym("STRING")));

    grammar.addSymbol("COMPOUND_VALUE", new Alt(grammar.sym("NEGATE_VALUE"),
      grammar.sym("OR_VALUE"), grammar.sym("AND_VALUE")));

    grammar.addSymbol("NEGATE_VALUE", new Seq(Literal.create("("),
      new Alt(Literal.create("-"), new LiteralIC("not")),
      grammar.sym("VALUE"), Literal.create(")")));

    grammar.addSymbol("OR_VALUE", new Seq1(1,
      Literal.create("("),
      new Repeat(
          grammar.sym("VALUE"),
          new Alt(Literal.create("|"), new LiteralIC(" or "), Literal.create(" | ")), 1
        ),
      Literal.create(")")
    ));

    grammar.addSymbol("AND_VALUE", new Seq(
      Literal.create("("),
      new Repeat(
        grammar.sym("VALUE"),
        new Alt(new LiteralIC(" and "), Literal.create(" ")), 1
      ),
      Literal.create(")")
    ));

    grammar.addSymbol("VALUE_LIST", new Alt(
      grammar.sym("COMPOUND_VALUE"),
      new Repeat(grammar.sym("VALUE"), Literal.create(","), 1)
    ));

    grammar.addSymbol("ME", new Seq(
      new LiteralIC("me"),
      new foam.lib.parse.Not(grammar.sym("char"))
    ));
    grammar.addAction("ME", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        if ( ( (Object[]) val )[0].equals("me") ) {
          User user = ((Subject) x.get("subject")).getUser();
          if ( user == null ) {
            foam.nanos.logger.Logger logger = (foam.nanos.logger.Logger) x.get("logger");
            logger.warning("user not logged in ");
            return val;
          }
          return user.getId();
        }
        return val;
      }
    });

    grammar.addSymbol("DATE", new Alt(
      grammar.sym("RANGE_DATE"),
      grammar.sym("LITERAL_DATE"),
      grammar.sym("RELATIVE_DATE")
    ));

    grammar.addSymbol("RANGE_DATE", new Seq(
      new Alt(grammar.sym("LITERAL_DATE"), grammar.sym("NUMBER")),
      Literal.create(".."),
      new Alt(grammar.sym("LITERAL_DATE"), grammar.sym("NUMBER"))
    ));
    grammar.addAction("RANGE_DATE", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        Object[] result = (Object[]) val;

        java.util.Calendar c = new java.util.GregorianCalendar();
        // d1..d2
        if (result.length > 10 && result[5].equals("..")) {
          Date date1 = null, date2 = null;
          c.set((Integer) result[0], (Integer) result[2] - 1, (Integer) result[4]);
          date1 = c.getTime();
          c.clear();
          c.set((Integer) result[6], (Integer) result[8] - 1, (Integer) result[10]);
          date2 = c.getTime();

          Date[] dates = new Date[]{date1, date2};
          return dates;
        }
        return val;
      }
    });

    grammar.addSymbol("LITERAL_DATE", new Alt(
      new Seq(
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER"),
        Literal.create("T"),
        grammar.sym("NUMBER"),
        Literal.create(":"),
        grammar.sym("NUMBER")
      ),
      new Seq(
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER"),
        Literal.create("T"),
        grammar.sym("NUMBER")
      ),
      new Seq(
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER")
      ),
      new Seq(
        grammar.sym("NUMBER"),
        Literal.create("-"),
        grammar.sym("NUMBER")
      ),
      new Seq(
        grammar.sym("NUMBER"),
        Literal.create("/"),
        grammar.sym("NUMBER"),
        Literal.create("/"),
        grammar.sym("NUMBER")
      ),
      //YYYY
      new Seq(
        grammar.sym("NUMBER"))
    ));
    grammar.addAction("LITERAL_DATE", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        java.util.Calendar c = new java.util.GregorianCalendar();
        c.clear();

        Object[] result = (Object[]) val;

        c.set(
          result.length >  1 ? (Integer) result[0]     : 0,
          result.length >  3 ? (Integer) result[2] - 1 : 0,
          result.length >  5 ? (Integer) result[4]     : 0,
          result.length >  7 ? (Integer) result[6]     : 0,
          result.length >  9 ? (Integer) result[8]     : 0,
          result.length > 11 ? (Integer) result[10]    : 0);

        return c.getTime();
      }
    });

    grammar.addSymbol("RELATIVE_DATE", new Seq1(0,
      new LiteralIC("today"),
      new Optional(new Seq(
        Literal.create("-"), grammar.sym("NUMBER")
      ))
    ));
    grammar.addAction("RELATIVE_DATE", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        // example today or today-7 {today,"-",7}
        Object[] result = null;
        result = new Integer[6];
        result[0] = new java.util.GregorianCalendar().getInstance().get(Calendar.YEAR);
        result[2] = new java.util.GregorianCalendar().getInstance().get(Calendar.MONTH) + 1;
        result[4] = new java.util.GregorianCalendar().getInstance().get(Calendar.DAY_OF_MONTH);

        java.util.Calendar c = new java.util.GregorianCalendar();

        Date date1, date2;
        c.clear();

        c.set(
          result.length > 1 ? (Integer) result[0] : 0,
          result.length > 3 ? (Integer) result[2] - 1 : 0,
          result.length > 5 ? (Integer) result[4] : 0,
          result.length > 7 ? (Integer) result[6] : 0,
          result.length > 9 ? (Integer) result[8] : 0,
          result.length > 11 ? (Integer) result[10] : 0);
        date1 = c.getTime();
        c.clear();

        Date[] dates;
        // {today,"-",7}
        if (val instanceof Object[]
          && (((Object[]) val).length < 4 && ((Object[]) val)[0] instanceof String)) {
          c.set(
            result.length > 1 ? (Integer) result[0] + (result.length > 3 ? 0 : 1) : 0,
            result.length > 3 ? (Integer) result[2] - 1 + (result.length > 5 ? 0 : 1) : 0,//TODO -1
            result.length > 5 ? (Integer) result[4] - (Integer)((Object[]) val)[2] : 0//to include that date
          );
          date2 = c.getTime();
          dates = new Date[] { date2, date1 };
        } else {//today
          c.clear();
          c.set(
            result.length > 1 ? (Integer) result[0] + (result.length > 3 ? 0 : 1) : 0,
            result.length > 3 ? (Integer) result[2] - 1 + (result.length > 5 ? 0 : 1) : 0,
            result.length > 5 ? (Integer) result[4] + (result.length > 7 ? 0 : 1) : 0,
            result.length > 7 ? (Integer) result[6] + (result.length > 9 ? 0 : 1) : 0,
            result.length > 9 ? (Integer) result[8] + (result.length > 11 ? 0 : 1) : 0,
            result.length > 11 ? (Integer) result[10] + (result.length > 13 ? 0 : 1) : 0);
          date2 = c.getTime();
          dates = new Date[] { date1, date2 };
        }
        return dates;
      }
    });

    grammar.addSymbol("STRING", new Alt(
      grammar.sym("WORD"),
      grammar.sym("QUOTED_STRING")
    ));

    grammar.addSymbol("QUOTED_STRING", new Seq1(1,
      Literal.create("\""),
      new Repeat(new Alt(
        Literal.create("\\\""),
        Literal.create("\"")
      ), new NotChars("\""))
    ));

    grammar.addSymbol("WORD", new Repeat(
      grammar.sym("CHAR"), 1
    ));
    grammar.addAction("WORD", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        return compactToString(val);
      }
    });

    grammar.addSymbol("CHAR", new Alt(
      Range.create('a', 'z'),
      Range.create('A', 'Z'),
      Range.create('0', '9'),
      Literal.create("-"),
      Literal.create("^"),
      Literal.create("_"),
      Literal.create("@"),
      Literal.create("%"),
      Literal.create(".")
    ));

    grammar.addSymbol("NUMBER", new Repeat(
      Range.create('0', '9'), 1
    ));
    grammar.addAction("NUMBER", new Action() {
      @Override
      public Object execute(Object val, ParserContext x) {
        String num = compactToString(val);
        if ( num.length() == 0 ) return val;
        return Integer.parseInt(num);
      }
    });

    return grammar;
  }

  protected String compactToString(Object val) {
    Object[] values = (Object[]) val;
    StringBuilder sb = new StringBuilder();
    for ( Object num: values ) {
      sb.append(num);
    }
    return sb.toString();
  }
}
