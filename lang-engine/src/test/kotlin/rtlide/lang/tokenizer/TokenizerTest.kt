package rtlide.lang.tokenizer

import org.junit.Test
import rtlide.lang.SakhrLang
import kotlin.test.assertEquals

class TokenizerTest {

    @Test
    fun testSakhrMultiWordKeyword() {
        val lang = SakhrLang.definition()
        val tokenizer = Tokenizer(lang.grammar)
        
        // Test "إن كان"
        val tokensIf = tokenizer.tokenize("إن كان (صح) ابدأ")
        val keywordIf = tokensIf.find { it.scope == "keyword.control.arabic" }
        assertEquals("إن كان", "إن كان (صح) ابدأ".substring(keywordIf!!.start, keywordIf.end))
        
        // Test "ما دام"
        val tokensWhile = tokenizer.tokenize("ما دام (صح) كرر")
        val keywordWhile = tokensWhile.find { it.scope == "keyword.control.arabic" }
        assertEquals("ما دام", "ما دام (صح) كرر".substring(keywordWhile!!.start, keywordWhile.end))
    }
}
