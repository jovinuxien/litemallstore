// Customer SPA — production bundle into the gateway-api jar static assets.
const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  mode: 'production',
  entry: path.resolve(__dirname, '../app/index.tsx'),
  output: {
    path: path.resolve(__dirname, '../../../../target/classes/static'),
    filename: 'app/[name].[contenthash].js',
    publicPath: '/',
    clean: true,
  },
  resolve: {
    extensions: ['.tsx', '.ts', '.js'],
    alias: {
      app: path.resolve(__dirname, '../app'),
      '@litemall/shared': path.resolve(__dirname, '../../../../../litemall-shared/src/index.ts'),
    },
  },
  module: {
    rules: [{ test: /\.tsx?$/, use: 'ts-loader', exclude: /node_modules/ }],
  },
  plugins: [new HtmlWebpackPlugin({ template: path.resolve(__dirname, '../public/index.html') })],
};