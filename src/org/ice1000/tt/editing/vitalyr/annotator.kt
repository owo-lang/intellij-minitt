package org.ice1000.tt.editing.vitalyr

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.ui.JBColor
import org.ice1000.tt.TTBundle
import org.ice1000.tt.VITALYR_LANGUAGE_NAME
import org.ice1000.tt.psi.leftSiblings
import org.ice1000.tt.psi.vitalyr.VitalyRExpr
import org.ice1000.tt.psi.vitalyr.VitalyRLambda
import org.ice1000.tt.psi.vitalyr.VitalyRTokenType
import org.ice1000.tt.psi.vitalyr.VitalyRTypes

object VitalyRHighlighter : VitalyRGeneratedHighlighter() {
	override fun getTokenHighlights(type: IElementType?): Array<TextAttributesKey> = when (type) {
		VitalyRTypes.IDENTIFIER -> IDENTIFIER_KEY
		VitalyRTypes.KW_LAMBDA -> KEYWORD_KEY
		VitalyRTokenType.LINE_COMMENT -> LINE_COMMENT_KEY
		VitalyRTypes.LPAREN, VitalyRTypes.RPAREN -> PAREN_KEY
		else -> emptyArray()
	}
}

class VitalyRAnnotator : Annotator, DumbAware {
	override fun annotate(element: PsiElement, holder: AnnotationHolder) {
		when (element) {
			is VitalyRLambda -> lambda(element, holder)
		}
	}

	private fun lambda(element: VitalyRLambda, holder: AnnotationHolder) {
		if (PsiTreeUtil.hasErrorElements(element)) return
		val expr = element.expr ?: return
		holder.createInfoAnnotation(expr, "null").registerFix(Eval(expr))
	}
}

private class Eval(val expr: VitalyRExpr) : BaseIntentionAction(), DumbAware {
	override fun getFamilyName() = VITALYR_LANGUAGE_NAME
	override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?) = true
	override fun getText() = TTBundle.message("vitalyr.lint.brutal-normalize", expr.text)
	private fun reportError(editor: Editor, e: EvaluationException) = ApplicationManager.getApplication().invokeLater {
		val loc = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
		JBPopupFactory.getInstance()
			.createHtmlTextBalloonBuilder(e.message.orEmpty(), null, JBColor.RED, null)
			.setFadeoutTime(8000)
			.setHideOnAction(true)
			.createBalloon()
			.show(loc, Balloon.Position.below)
	}

	override operator fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
		if (PsiTreeUtil.hasErrorElements(expr)) return
		val (ctx, term) = try {
			ApplicationManager.getApplication().runReadAction<Pair<Ctx, Term>, EvaluationException> {
				expr.parentOfType<VitalyRLambda>()
					?.leftSiblings
					.orEmpty()
					.filterIsInstance<VitalyRLambda>()
					.filterNot(PsiTreeUtil::hasErrorElements)
					.mapNotNull { it.nameDecl?.text?.let { a -> it.expr?.let(::fromPsi)?.let { b -> a to b } } }
					.toList() to fromPsi(expr)
			}
		} catch (e: EvaluationException) {
			if (editor == null) return
			reportError(editor, e)
			return
		}
		var result: String? = null
		ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Normalizing", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
			override fun run(indicator: ProgressIndicator) {
				val string = StringBuilder()
				try {
					normalize(term, ctx).toString(string, ToStrCtx.AbsBody)
				} catch (e: EvaluationException) {
					if (editor == null) return
					reportError(editor, e)
				}
				result = string.toString()
			}

			override fun onFinished() = WriteCommandAction.runWriteCommandAction(project) {
				result?.let { VitalyRTokenType.createExpr(it, project) }?.let(expr::replace)
			}
		})
	}
}