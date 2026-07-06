// Ambient module declarations so ts-loader accepts non-TS imports (styles,
// fonts, images) that webpack handles via style/css/sass-loader and asset/resource.
declare module '*.scss';
declare module '*.css';
declare module '*.svg' {
  const content: string;
  export default content;
}
declare module '*.png';
declare module '*.jpg';
declare module '*.jpeg';
declare module '*.gif';
declare module '*.webp';
