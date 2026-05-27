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


if __name__ == "__main__":
    unittest.main()
