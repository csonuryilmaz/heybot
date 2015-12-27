<?php

/**
* Below utility class converts number to text for Turkish money.
* @author: onur yılmaz
*/

class NumberToTextConverter
{
	private static $digits_birler = array("", "Bir", "İki", "Üç", "Dört", "Beş", "Altı", "Yedi", "Sekiz", "Dokuz");
	private static $digits_onlar = array("", "On", "Yirmi", "Otuz", "Kırk", "Elli", "Altmış", "Yetmiş", "Seksen", "Doksan");
	private static $digits_binler = array("", "Bin", "Milyon", "Milyar", "Trilyon");
	
	private static function convert($number)
	{
		if ($number > 0)
		{
			$number = "" . (int)$number;// if there is leading 0, then get rid of them
			$digit_count = strlen($number);
			$fraction = (int)($digit_count / 3);
			$remainder = $digit_count % 3;
			
			$text = "";
			// remainder=0
			for ($i=0;$i < $fraction; $i++)
			{
				$yuzler = ($i * 3) + $remainder;
				$onlar = ($i * 3) + $remainder + 1;
				$birler = ($i * 3) + $remainder + 2;
				
				$text .= ($number[$yuzler] > 1 ? self::$digits_birler[(int)$number[$yuzler]] : "") . "Yüz"  . self::$digits_onlar[(int)$number[$onlar]] . self::$digits_birler[(int)$number[$birler]] . self::$digits_binler[$fraction-($i+1)];
			}
			
			if ($remainder==2)
			{
				return self::$digits_onlar[(int)$number[0]] . self::$digits_birler[(int)$number[1]] . self::$digits_binler[$fraction-($remainder > 0 ? 0 :1)] . $text;
			}
			
			if ($remainder==1)
			{
				return self::$digits_birler[(int)$number[0]] . self::$digits_binler[$fraction-($remainder > 0 ? 0 : 1)] . $text;
			}
			
			return $text;
		}
		return "";
	}
	
	static function convertTRYMoney($amount, $seperator = ',')
	{
		$text = "";
		$parts = explode($seperator, $amount);// split amount into TL and KRŞ
		if (count($parts) == 2 && $parts[0] >= 0 && $parts[1] >= 0)
		{// has two parts && only positive amounts
			$part_tl = self::convert($parts[0]);// convert TL into text
			$part_krs = self::convert($parts[1]);// convert KRŞ into text
			if ($part_tl)
			{
				$text .= $part_tl . ' Türk Lirası ';
			}
			if ($part_krs)
			{
				$text .= $part_krs . ' Kuruş ';
			}
		}
		return $text;
	}
}

// There are some examples:
echo '0,00 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('0,00');
echo '<br/>';
echo '0,05 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('0,05');
echo '<br/>';
echo '0,12 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('0,12');
echo '<br/>';
echo '0,87 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('0,87');
echo '<br/>';
echo '1,87 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('1,87');
echo '<br/>';
echo '32,05 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('32,05');
echo '<br/>';
echo '163,11 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('163,11');
echo '<br/>';
echo '2163,25 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('2163,25');
echo '<br/>';
echo '52644,92 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('52644,92');
echo '<br/>';
echo '277900,00 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('277900,00');
echo '<br/>';
echo '1747533,92 TL' . ' --> ' . NumberToTextConverter::convertTRYMoney('1747533,92');
?>
