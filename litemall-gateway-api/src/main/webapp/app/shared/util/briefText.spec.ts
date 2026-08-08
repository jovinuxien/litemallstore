import { briefToText } from './briefText';

describe('briefToText', () => {
  it('passes plain text through trimmed', () => {
    expect(briefToText('  Insulated stainless bottle  ')).toBe('Insulated stainless bottle');
  });

  it('collapses an image-only CJ brief to empty', () => {
    expect(briefToText('<p><img src="https://oss-cf.cjdropshipping.com/x.jpg" style="max-width:100%;" contenteditable="false"/></p>')).toBe(
      '',
    );
  });

  it('keeps the text content of an HTML brief and collapses whitespace', () => {
    expect(briefToText('<p>40oz  tumbler<br/>with <strong>FreeSip</strong> lid</p>')).toBe('40oz tumblerwith FreeSip lid');
  });

  it('returns empty for null/undefined/empty input', () => {
    expect(briefToText(undefined)).toBe('');
    expect(briefToText(null)).toBe('');
    expect(briefToText('')).toBe('');
  });
});
