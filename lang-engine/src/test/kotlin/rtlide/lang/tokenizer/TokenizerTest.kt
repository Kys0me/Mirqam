package rtlide.lang.tokenizer

import org.junit.Test
import rtlide.lang.SakhrLang
import kotlin.test.assertEquals

class TokenizerTest {

    @Test
    fun testSakhrMultiWordKeyword() {
        val lang = SakhrLang.definition()
        val tokenizer = Tokenizer(lang.grammar)
        val tokens = tokenizer.tokenize("إن كان (صح) ابدأ")
        
        // Expected:
        // "إن كان" -> keyword.control.arabic
        // " " -> ignored
        // "(" -> default (skipped in this simple tokenizer but affects indices)
        // "صح" -> constant.language.arabic
        // ")" -> default
        // " " -> ignored
        // "ابدأ" -> storage.type.arabic (it's in storage/keywords in SakhrLang.kt)
        
        val keywordToken = tokens.find { it.scope == "keyword.control.arabic" }
        assertEquals("إن كان", "إن كان (صح) ابدأ".substring(keywordToken!!.start, keywordToken.end))
        
        val constantToken = tokens.find { it.scope == "constant.language.arabic" }
        assertEquals("صح", "إن كان (صح) ابدأ".substring(constantToken!!.start, constantToken.end))
    }
}
