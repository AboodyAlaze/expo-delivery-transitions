Pod::Spec.new do |s|
  s.name           = 'ExpoDeliveryTransitions'
  s.version        = '0.1.0'
  s.summary        = 'Creative native page transitions for Expo Router'
  s.description    = 'Delivery bike / car (with your logo), liquid pour, ball zoom, radar, hex and curtain transitions for Expo Router.'
  s.license        = { :type => 'MIT' }
  s.author         = 'expo-delivery-transitions'
  s.homepage       = 'https://github.com/expo/expo'
  s.platforms      = {
    :ios => '16.4'
  }
  s.swift_version  = '5.9'
  s.source         = { git: 'https://github.com/expo/expo.git' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'

  s.source_files = "**/*.{h,m,swift}"
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }
end
