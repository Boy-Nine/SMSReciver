import unittest

from services.code_extractor import extract_verification_code


class TestCodeExtractor(unittest.TestCase):
    def test_extract_from_chinese_template(self):
        body = "您的验证码是123456，5分钟内有效"
        self.assertEqual(extract_verification_code(body), "123456")

    def test_extract_from_bracket_template(self):
        body = "【测试】您的验证码是654321，请勿泄露。"
        self.assertEqual(extract_verification_code(body), "654321")

    def test_extract_none_when_missing(self):
        body = "您的订单已发货，请注意查收。"
        self.assertIsNone(extract_verification_code(body))

    def test_extract_code_before_keyword(self):
        body = "887766是您的登录验证码，5分钟内有效。"
        self.assertEqual(extract_verification_code(body), "887766")

    def test_extract_from_bracket_digits(self):
        body = "【测试平台】请使用【445566】完成身份验证。"
        self.assertEqual(extract_verification_code(body), "445566")

    def test_extract_fullwidth_digits(self):
        body = "您的验证码是１２３４５６，请勿泄露。"
        self.assertEqual(extract_verification_code(body), "123456")


if __name__ == "__main__":
    unittest.main()
