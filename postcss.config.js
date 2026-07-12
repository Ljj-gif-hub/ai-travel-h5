export default {
  plugins: {
    'postcss-pxtorem': {
      rootValue: 37.5,
      propList: ['*'],
      selectorBlackList: [],
      minPixelValue: 1,
      replace: true,
      mediaQuery: false,
      exclude: /node_modules/i,
    },
  },
}
