package com.snapstreakrecoverer.ssr.recovery

object RecoveryScript {

    /** Escapes a value so it can be safely embedded inside a single-quoted JS string. */
    private fun esc(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    fun getFillFormScript(
        username: String,
        email: String,
        mobileNumber: String,
        device: String,
        friendUsername: String
    ): String {
        val u = esc(username)
        val e = esc(email)
        val m = esc(mobileNumber)
        val d = esc(device)
        val f = esc(friendUsername)
        return """
            (function() {
                async function fillForm() {
                    console.log('Filling form for: $f');

                    const waitForSelector = (selector, timeout = 10000) => {
                        return new Promise((resolve) => {
                            const el = document.querySelector(selector);
                            if (el) return resolve(el);
                            const observer = new MutationObserver(() => {
                                const el = document.querySelector(selector);
                                if (el) { observer.disconnect(); resolve(el); }
                            });
                            observer.observe(document.body, { childList: true, subtree: true });
                            setTimeout(() => { observer.disconnect(); resolve(null); }, timeout);
                        });
                    };

                    await waitForSelector('#request_custom_fields_24281229', 15000);

                    function fillByLabel(searchText, value) {
                        const labels = Array.from(document.querySelectorAll('label'));
                        const targetLabel = labels.find(l => l.textContent.includes(searchText));
                        if (targetLabel) {
                            const input = document.getElementById(targetLabel.getAttribute('for')) ||
                                          targetLabel.parentElement.querySelector('input, textarea');
                            if (input) {
                                input.value = value;
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                                input.dispatchEvent(new Event('change', { bubbles: true }));
                                return true;
                            }
                        }
                        return false;
                    }

                    const today = new Date().toISOString().split('T')[0];
                    const fields = [
                        ['#request_custom_fields_24281229', '$u', 'Username'],
                        ['#request_custom_fields_24335325', '$e', 'Email'],
                        ['#request_custom_fields_24369716', '$m', 'Mobile Number'],
                        ['#request_custom_fields_24335345', '$d', 'Device'],
                        ['#request_custom_fields_24369736', '$f', "Friend's Username"],
                        ['#request_custom_fields_24369756', today, 'Date'],
                        ['#request_description', 'My snapstreak disappeared recently without any reason. Please restore it.', 'Description'],
                    ];

                    for (const [selector, value, labelHint] of fields) {
                        try {
                            let el = document.querySelector(selector);
                            if (!el && labelHint) {
                                fillByLabel(labelHint, value);
                                continue;
                            }

                            if (el && value) {
                                const prototype = el.tagName === 'TEXTAREA'
                                    ? window.HTMLTextAreaElement.prototype
                                    : window.HTMLInputElement.prototype;

                                const nativeSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
                                if (nativeSetter) {
                                    nativeSetter.call(el, value);
                                } else {
                                    el.value = value;
                                }
                                el.dispatchEvent(new Event('input', { bubbles: true }));
                                el.dispatchEvent(new Event('change', { bubbles: true }));
                                el.dispatchEvent(new Event('blur', { bubbles: true }));
                            }
                        } catch (e) { console.warn('Field fill error:', selector, e); }
                    }

                    // Auto-submit. If a captcha is required, Snapchat blocks the
                    // submit until the user solves it; the click still primes it.
                    setTimeout(() => {
                        const submitSelectors = ['input[type="submit"]', 'button[type="submit"]', 'input[name="commit"]'];
                        for (const sel of submitSelectors) {
                            const btn = document.querySelector(sel);
                            if (btn) {
                                btn.click();
                                try { if (window.Android && Android.onSubmitClicked) Android.onSubmitClicked(); } catch (e) {}
                                break;
                            }
                        }
                    }, 1000);
                }
                fillForm().catch(function(err) {
                    console.warn('fillForm failed:', err);
                    try { if (window.Android && Android.onFillFailed) Android.onFillFailed(String(err)); } catch (e) {}
                });
            })();
        """.trimIndent()
    }
}
