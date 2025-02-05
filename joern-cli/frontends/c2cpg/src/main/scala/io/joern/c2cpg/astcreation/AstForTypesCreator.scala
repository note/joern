package io.joern.c2cpg.astcreation

import io.joern.c2cpg.datastructures.Stack._
import io.shiftleft.codepropertygraph.generated.nodes._
import io.shiftleft.codepropertygraph.generated.{DispatchTypes, Operators}
import io.shiftleft.codepropertygraph.generated.NodeTypes
import io.joern.x2cpg.Ast
import org.eclipse.cdt.core.dom.ast._
import org.eclipse.cdt.core.dom.ast.cpp._
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTAliasDeclaration
import org.eclipse.cdt.internal.core.model.ASTStringUtil

trait AstForTypesCreator {

  this: AstCreator =>

  import AstCreatorHelper.OptionSafeAst

  private def parentIsClassDef(node: IASTNode): Boolean = Option(node.getParent) match {
    case Some(_: IASTCompositeTypeSpecifier) => true
    case _                                   => false
  }

  private def isTypeDef(decl: IASTSimpleDeclaration): Boolean =
    nodeSignature(decl).startsWith("typedef") ||
      decl.getDeclSpecifier.isInstanceOf[IASTCompositeTypeSpecifier] ||
      decl.getDeclSpecifier.isInstanceOf[IASTEnumerationSpecifier]

  protected def templateParameters(e: IASTNode): Option[String] = {
    val templateDeclaration = e match {
      case _: IASTElaboratedTypeSpecifier | _: IASTFunctionDeclarator | _: IASTCompositeTypeSpecifier
          if e.getParent != null =>
        Some(e.getParent.getParent)
      case _: IASTFunctionDefinition if e.getParent != null =>
        Some(e.getParent)
      case _ => None
    }

    val decl           = templateDeclaration.collect { case t: ICPPASTTemplateDeclaration => t }
    val templateParams = decl.map(d => ASTStringUtil.getTemplateParameterArray(d.getTemplateParameters))
    templateParams.map(_.mkString("<", ",", ">"))
  }

  private def astForNamespaceDefinition(namespaceDefinition: ICPPASTNamespaceDefinition, order: Int): Ast = {
    val linenumber   = line(namespaceDefinition)
    val columnnumber = column(namespaceDefinition)
    val filename     = fileName(namespaceDefinition)

    val (name, fullname) =
      uniqueName("namespace", namespaceDefinition.getName.getLastName.toString, fullName(namespaceDefinition))
    val code = "namespace " + fullname
    val cpgNamespace = NewNamespaceBlock()
      .code(code)
      .lineNumber(linenumber)
      .columnNumber(columnnumber)
      .filename(filename)
      .name(name)
      .fullName(fullname)
      .order(order)

    scope.pushNewScope(cpgNamespace)
    var currOrder = order
    val childrenAsts = namespaceDefinition.getDeclarations.flatMap { decl =>
      val declAsts = astsForDeclaration(decl, currOrder)
      currOrder = currOrder + declAsts.length
      declAsts
    }.toIndexedSeq

    val namespaceAst = Ast(cpgNamespace).withChildren(childrenAsts)
    scope.popScope()
    namespaceAst
  }

  protected def astForNamespaceAlias(namespaceAlias: ICPPASTNamespaceAlias, order: Int): Ast = {
    val linenumber   = line(namespaceAlias)
    val columnnumber = column(namespaceAlias)
    val filename     = fileName(namespaceAlias)

    val name     = ASTStringUtil.getSimpleName(namespaceAlias.getAlias)
    val fullname = fullName(namespaceAlias)

    if (!isQualifiedName(name)) {
      usingDeclarationMappings.put(name, fullname)
    }

    val code = "namespace " + name + " = " + fullname
    val cpgNamespace = NewNamespaceBlock()
      .code(code)
      .lineNumber(linenumber)
      .columnNumber(columnnumber)
      .filename(filename)
      .name(name)
      .fullName(fullname)
      .order(order)

    Ast(cpgNamespace)
  }

  protected def astForDeclarator(declaration: IASTSimpleDeclaration, declarator: IASTDeclarator, order: Int): Ast = {
    val declTypeName = registerType(typeForDeclSpecifier(declaration.getDeclSpecifier))
    val tpe          = typeFor(declarator)
    val name         = ASTStringUtil.getSimpleName(declarator.getName)
    declaration match {
      case d if isTypeDef(d) =>
        val filename = fileName(declaration)
        Ast(
          newTypeDecl(name, registerType(name), filename, nodeSignature(d), alias = Some(declTypeName), order = order)
        )
      case d if parentIsClassDef(d) =>
        Ast(
          NewMember()
            .code(nodeSignature(declarator))
            .name(name)
            .typeFullName(declTypeName)
            .order(order)
        )
      case _ if declarator.isInstanceOf[IASTArrayDeclarator] =>
        val l = NewLocal()
          .code(s"$tpe $name")
          .name(name)
          .typeFullName(registerType(tpe))
          .order(order)
          .lineNumber(line(declarator))
        scope.addToScope(name, (l, tpe))
        Ast(l)
      case _ =>
        val l = NewLocal()
          .code(s"$declTypeName $name")
          .name(name)
          .typeFullName(registerType(tpe))
          .order(order)
          .lineNumber(line(declarator))
        scope.addToScope(name, (l, tpe))
        Ast(l)
    }

  }

  protected def astForInitializer(declarator: IASTDeclarator, init: IASTInitializer, order: Int): Ast = init match {
    case i: IASTEqualsInitializer =>
      val operatorName = Operators.assignment
      val callNode     = newCallNode(declarator, operatorName, operatorName, DispatchTypes.STATIC_DISPATCH, order)
      val left         = astForNode(declarator.getName, 1)
      val right        = astForNode(i.getInitializerClause, 2)
      Ast(callNode)
        .withChild(left)
        .withChild(right)
        .withArgEdge(callNode, left.root)
        .withArgEdge(callNode, right.root)
    case i: ICPPASTConstructorInitializer =>
      val name     = ASTStringUtil.getSimpleName(declarator.getName)
      val callNode = newCallNode(declarator, name, name, DispatchTypes.STATIC_DISPATCH, order)
      val args     = withOrder(i.getArguments) { case (a, o) => astForNode(a, o) }
      Ast(callNode).withChildren(args).withArgEdges(callNode, args)
    case i: IASTInitializerList =>
      val operatorName = Operators.assignment
      val callNode     = newCallNode(declarator, operatorName, operatorName, DispatchTypes.STATIC_DISPATCH, order)
      val left         = astForNode(declarator.getName, 1)
      val right        = astForNode(i, 2)
      Ast(callNode)
        .withChild(left)
        .withChild(right)
        .withArgEdge(callNode, left.root)
        .withArgEdge(callNode, right.root)
    case _ => astForNode(init, order)
  }

  protected def handleUsingDeclaration(usingDecl: ICPPASTUsingDeclaration): Seq[Ast] = {
    val simpleName = ASTStringUtil.getSimpleName(usingDecl.getName)
    val mappedName = lastNameOfQualifiedName(simpleName)
    // we only do the mapping if the declaration is not global because this is already handled by the parser itself
    if (!isQualifiedName(simpleName)) {
      usingDecl.getParent match {
        case ns: ICPPASTNamespaceDefinition =>
          usingDeclarationMappings.put(fullName(ns) + "." + mappedName, fixQualifiedName(simpleName))
        case _ =>
          usingDeclarationMappings.put(mappedName, fixQualifiedName(simpleName))
      }
    }
    Seq.empty
  }

  protected def astForAliasDeclaration(aliasDeclaration: ICPPASTAliasDeclaration, order: Int): Ast = {
    val linenumber   = line(aliasDeclaration)
    val columnnumber = column(aliasDeclaration)
    val filename     = fileName(aliasDeclaration)

    val name       = aliasDeclaration.getAlias.toString
    val mappedName = registerType(typeFor(aliasDeclaration.getMappingTypeId))
    val typeDeclNode =
      newTypeDecl(
        name,
        registerType(name),
        filename,
        mappedName,
        alias = Some(mappedName),
        line = linenumber,
        column = columnnumber,
        order = order
      )
    Ast(typeDeclNode)
  }

  protected def astForASMDeclaration(asm: IASTASMDeclaration, order: Int): Ast = Ast(newUnknown(asm, order))

  private def astForStructuredBindingDeclaration(
    structuredBindingDeclaration: ICPPASTStructuredBindingDeclaration,
    order: Int
  ): Ast = {
    val cpgBlock = NewBlock()
      .order(order)
      .argumentIndex(order)
      .typeFullName(registerType(Defines.voidTypeName))
      .lineNumber(line(structuredBindingDeclaration))
      .columnNumber(column(structuredBindingDeclaration))

    scope.pushNewScope(cpgBlock)
    val childAsts = withOrder(structuredBindingDeclaration.getNames) { case (name, o) =>
      astForNode(name, o)
    }

    val blockAst = Ast(cpgBlock).withChildren(childAsts)
    scope.popScope()
    blockAst
  }

  protected def astsForDeclaration(decl: IASTDeclaration, order: Int): Seq[Ast] = {
    val declAsts = decl match {
      case sb: ICPPASTStructuredBindingDeclaration => Seq(astForStructuredBindingDeclaration(sb, order))
      case declaration: IASTSimpleDeclaration =>
        declaration.getDeclSpecifier match {
          case spec: IASTCompositeTypeSpecifier =>
            astsForCompositeType(spec, declaration.getDeclarators.toList, order)
          case spec: IASTEnumerationSpecifier =>
            astsForEnum(spec, declaration.getDeclarators.toList, order)
          case spec: IASTElaboratedTypeSpecifier =>
            astsForElaboratedType(spec, declaration.getDeclarators.toList, order)
          case spec: IASTNamedTypeSpecifier if declaration.getDeclarators.isEmpty =>
            val filename = fileName(spec)
            val name     = ASTStringUtil.getSimpleName(spec.getName)
            Seq(Ast(newTypeDecl(name, registerType(name), filename, name, alias = Some(name), order = order)))
          case _ if declaration.getDeclarators.nonEmpty =>
            declaration.getDeclarators.toIndexedSeq.map {
              case d: IASTFunctionDeclarator =>
                astForFunctionDeclarator(d, order)
              case d: IASTSimpleDeclaration if d.getInitializer != null =>
                Ast() // we do the AST for this down below with initAsts
              case d =>
                astForDeclarator(declaration, d, order)
            }
          case _ if nodeSignature(declaration) == ";" =>
            Seq.empty // dangling decls from unresolved macros; we ignore them
          case _ if declaration.getDeclarators.isEmpty && declaration.getParent.isInstanceOf[IASTTranslationUnit] =>
            Seq.empty // dangling decls from unresolved macros; we ignore them
          case _ if declaration.getDeclarators.isEmpty => Seq(astForNode(declaration, order))
        }
      case alias: CPPASTAliasDeclaration                   => Seq(astForAliasDeclaration(alias, order))
      case functDef: IASTFunctionDefinition                => Seq(astForFunctionDefinition(functDef, order))
      case namespaceAlias: ICPPASTNamespaceAlias           => Seq(astForNamespaceAlias(namespaceAlias, order))
      case namespaceDefinition: ICPPASTNamespaceDefinition => Seq(astForNamespaceDefinition(namespaceDefinition, order))
      case a: ICPPASTStaticAssertDeclaration               => Seq(astForStaticAssert(a, order))
      case asm: IASTASMDeclaration                         => Seq(astForASMDeclaration(asm, order))
      case t: ICPPASTTemplateDeclaration                   => astsForDeclaration(t.getDeclaration, order)
      case l: ICPPASTLinkageSpecification                  => astsForLinkageSpecification(l)
      case u: ICPPASTUsingDeclaration                      => handleUsingDeclaration(u)
      case _: ICPPASTVisibilityLabel                       => Seq.empty
      case _: ICPPASTUsingDirective                        => Seq.empty
      case _: ICPPASTExplicitTemplateInstantiation         => Seq.empty
      case _                                               => Seq(astForNode(decl, order))
    }

    val lastOrder = declAsts.length
    val initAsts = decl match {
      case declaration: IASTSimpleDeclaration if declaration.getDeclarators.nonEmpty =>
        withOrder(declaration.getDeclarators) {
          case (d: IASTDeclarator, o) if d.getInitializer != null =>
            astForInitializer(d, d.getInitializer, order + lastOrder + o - 1)
          case _ => Ast()
        }
      case _ => Nil
    }
    declAsts ++ initAsts
  }

  private def astsForLinkageSpecification(l: ICPPASTLinkageSpecification): Seq[Ast] =
    withOrder(l.getDeclarations) { case (d, o) =>
      astsForDeclaration(d, o)
    }.flatten

  private def astsForCompositeType(
    typeSpecifier: IASTCompositeTypeSpecifier,
    decls: List[IASTDeclarator],
    order: Int
  ): Seq[Ast] = {
    val filename = fileName(typeSpecifier)
    val declAsts = withOrder(decls) { (d, o) =>
      astForDeclarator(typeSpecifier.getParent.asInstanceOf[IASTSimpleDeclaration], d, order + o)
    }

    val name                   = ASTStringUtil.getSimpleName(typeSpecifier.getName)
    val fullname               = registerType(cleanType(fullName(typeSpecifier)))
    val code                   = typeFor(typeSpecifier)
    val nameWithTemplateParams = templateParameters(typeSpecifier).map(t => registerType(fullname + t))

    val typeDecl = typeSpecifier match {
      case cppClass: ICPPASTCompositeTypeSpecifier =>
        val baseClassList =
          cppClass.getBaseSpecifiers.toSeq.map(s => registerType(s.getNameSpecifier.toString))
        newTypeDecl(
          name,
          fullname,
          filename,
          code,
          inherits = baseClassList,
          alias = nameWithTemplateParams,
          order = order
        )
      case _ =>
        newTypeDecl(name, fullname, filename, code, alias = nameWithTemplateParams, order = order)
    }

    methodAstParentStack.push(typeDecl)
    scope.pushNewScope(typeDecl)

    val memberAsts = withOrder(typeSpecifier.getDeclarations(true)) { (m, o) =>
      astsForDeclaration(m, o)
    }.flatten

    methodAstParentStack.pop()
    scope.popScope()

    val (calls, member) = memberAsts.partition(_.nodes.headOption.exists(_.isInstanceOf[NewCall]))
    if (calls.isEmpty) {
      Ast(typeDecl).withChildren(member) +: declAsts
    } else {
      val init = astForFakeStaticInitMethod(
        fullname,
        line(typeSpecifier),
        NodeTypes.TYPE_DECL,
        fullname,
        member.length + 1,
        calls
      )
      Ast(typeDecl).withChildren(member).withChild(init) +: declAsts
    }
  }

  private def astsForElaboratedType(
    typeSpecifier: IASTElaboratedTypeSpecifier,
    decls: List[IASTDeclarator],
    order: Int
  ): Seq[Ast] = {
    val filename = fileName(typeSpecifier)
    val declAsts = withOrder(decls) { (d, o) =>
      astForDeclarator(typeSpecifier.getParent.asInstanceOf[IASTSimpleDeclaration], d, order + o)
    }

    val name                   = ASTStringUtil.getSimpleName(typeSpecifier.getName)
    val fullname               = registerType(cleanType(fullName(typeSpecifier)))
    val nameWithTemplateParams = templateParameters(typeSpecifier).map(t => registerType(fullname + t))

    val typeDecl =
      newTypeDecl(name, fullname, filename, typeFor(typeSpecifier), alias = nameWithTemplateParams, order = order)

    Ast(typeDecl) +: declAsts
  }

  private def astsForEnumerator(enumerator: IASTEnumerationSpecifier.IASTEnumerator, order: Int): Seq[Ast] = {
    val tpe = enumerator.getParent match {
      case enumeration: ICPPASTEnumerationSpecifier if enumeration.getBaseType != null =>
        enumeration.getBaseType.toString
      case _ => typeFor(enumerator)
    }
    val cpgMember = NewMember()
      .code(nodeSignature(enumerator))
      .name(ASTStringUtil.getSimpleName(enumerator.getName))
      .typeFullName(registerType(cleanType(tpe)))
      .order(order)

    if (enumerator.getValue != null) {
      val operatorName = Operators.assignment
      val callNode     = newCallNode(enumerator, operatorName, operatorName, DispatchTypes.STATIC_DISPATCH, order + 1)
      val left         = astForNode(enumerator.getName, 1)
      val right        = astForNode(enumerator.getValue, 2)
      val ast = Ast(callNode)
        .withChild(left)
        .withChild(right)
        .withArgEdge(callNode, left.root)
        .withArgEdge(callNode, right.root)
      Seq(Ast(cpgMember), ast)
    } else {
      Seq(Ast(cpgMember))
    }

  }

  private def astsForEnum(
    typeSpecifier: IASTEnumerationSpecifier,
    decls: List[IASTDeclarator],
    order: Int
  ): Seq[Ast] = {
    val filename = fileName(typeSpecifier)
    val declAsts = withOrder(decls) { (d, o) =>
      astForDeclarator(typeSpecifier.getParent.asInstanceOf[IASTSimpleDeclaration], d, order + o)
    }

    val (name, fullname) =
      uniqueName("enum", ASTStringUtil.getSimpleName(typeSpecifier.getName), fullName(typeSpecifier))
    val typeDecl = newTypeDecl(name, registerType(fullname), filename, typeFor(typeSpecifier), order = order)

    methodAstParentStack.push(typeDecl)
    scope.pushNewScope(typeDecl)

    var currentOrder = 0
    val memberAsts = typeSpecifier.getEnumerators.toIndexedSeq.flatMap { e =>
      val eCpg = astsForEnumerator(e, currentOrder)
      currentOrder = eCpg.size + currentOrder
      eCpg
    }
    methodAstParentStack.pop()
    scope.popScope()

    val (calls, member) = memberAsts.partition(_.nodes.headOption.exists(_.isInstanceOf[NewCall]))
    if (calls.isEmpty) {
      Ast(typeDecl).withChildren(member) +: declAsts
    } else {
      val init = astForFakeStaticInitMethod(
        fullname,
        line(typeSpecifier),
        NodeTypes.TYPE_DECL,
        fullname,
        member.length + 1,
        calls
      )
      Ast(typeDecl).withChildren(member).withChild(init) +: declAsts
    }
  }

}
