import React from 'react';

/**
 * Trovemo wordmark — dark-surface variant of doc/brand/logo-wordmark.svg:
 * no background rect, "trovem" fills with currentColor (so the surrounding
 * text colour decides light/dark), while the orange open-"o" and the amber
 * spark are verbatim from the brand asset and must not be restyled.
 * The viewBox is cropped to the ink bounds so the mark sizes predictably.
 */
const TrovemoWordmark: React.FC<{ height?: number; className?: string }> = ({ height = 30, className }) => (
  <svg
    xmlns='http://www.w3.org/2000/svg'
    viewBox='56 88 738 118'
    height={height}
    className={className}
    role='img'
    aria-label='Trovemo'
  >
    <text
      x='64'
      y='196'
      fontFamily='Verdana, DejaVu Sans, sans-serif'
      fontWeight='bold'
      fontSize='124'
      fill='currentColor'
      textLength='590'
      lengthAdjust='spacingAndGlyphs'
    >
      trovem
    </text>
    <circle
      cx='716'
      cy='152'
      r='38'
      fill='none'
      stroke='#F09000'
      strokeWidth='24'
      strokeLinecap='round'
      strokeDasharray='196 43'
      transform='rotate(-55 716 152)'
    />
    <path d='M754 92 L762 116 L786 124 L762 132 L754 156 L746 132 L722 124 L746 116 Z' fill='#FFC24B' />
  </svg>
);

export default TrovemoWordmark;
